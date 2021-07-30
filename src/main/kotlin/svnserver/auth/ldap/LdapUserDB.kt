/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.ldap

import com.unboundid.ldap.sdk.*
import com.unboundid.util.ssl.SSLUtil
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import svnserver.Loggers
import svnserver.UserType
import svnserver.auth.Authenticator
import svnserver.auth.PlainAuthenticator
import svnserver.auth.User
import svnserver.auth.UserDB
import svnserver.auth.ldap.config.LdapUserDBConfig
import svnserver.config.ConfigHelper
import svnserver.context.SharedContext
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory

/**
 * LDAP authentication.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class LdapUserDB(context: SharedContext, config: LdapUserDBConfig) : UserDB {
    private val authenticators: Collection<Authenticator> = setOf(PlainAuthenticator(this))
    private val pool: LDAPConnectionPool
    private val config: LdapUserDBConfig
    private val baseDn: String
    private val fakeMailSuffix: String?
    override fun close() {
        pool.close()
    }

    override fun authenticators(): Collection<Authenticator> {
        return authenticators
    }

    @Throws(SVNException::class)
    override fun check(username: String, password: String): User? {
        return findUser(username) { userDN: String? -> pool.bindAndRevertAuthentication(userDN, password).resultCode === ResultCode.SUCCESS }
    }

    @Throws(SVNException::class)
    override fun lookupByUserName(username: String): User? {
        return findUser(username) { true }
    }

    override fun lookupByExternal(external: String): User? {
        return null
    }

    @Throws(SVNException::class)
    private fun findUser(username: String, ldapCheck: LdapCheck): User? {
        log.debug("LDAP lookup for user: {}", username)
        val entry: SearchResultEntry = try {
            val filter = config.createSearchFilter(username)
            log.debug("LDAP search filter: {}", filter)
            val search = pool.search(baseDn, SearchScope.SUB, filter, config.loginAttribute, config.nameAttribute, config.emailAttribute)
            log.debug("LDAP search result: {}", search)
            if (search.entryCount < 1) {
                log.info("User not found in LDAP: {}. Rejecting authentication", username)
                return null
            }
            if (search.entryCount > 1) {
                log.warn("Non-unique LDAP result for user: {}. Rejecting authentication", username)
                return null
            }
            search.searchEntries[0]
        } catch (e: LDAPException) {
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_NO_PROVIDER, e.message), e)
        }
        val login = getAttribute(entry, config.loginAttribute) ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_NO_PROVIDER, String.format("LDAP entry doesn't contain username for user: %s. Please, recheck 'loginAttribute' in git-as-svn LDAP configuration", username)))
        try {
            if (!ldapCheck.check(entry.dn)) {
                log.info("LDAP check failed for user: {}. Rejecting authentication", username)
                return null
            }
        } catch (e: LDAPException) {
            if (e.resultCode === ResultCode.INVALID_CREDENTIALS) {
                log.info("Invalid LDAP credentials for user: {}. Rejecting authentication", username)
                return null
            }
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHN_NO_PROVIDER, e.message, e))
        }
        val realName = getAttribute(entry, config.nameAttribute)
        var email = getAttribute(entry, config.emailAttribute)
        if (email == null && fakeMailSuffix != null) email = login + fakeMailSuffix
        log.debug("LDAP authentication successful for user: {}", username)
        return User.create(login, realName ?: login, email, null, UserType.LDAP, null)
    }

    private fun getAttribute(entry: SearchResultEntry, name: String): String? {
        val attribute = entry.getAttribute(name)
        return attribute.value
    }

    private fun interface LdapCheck {
        @Throws(LDAPException::class)
        fun check(userDN: String): Boolean
    }

    companion object {
        private val log = Loggers.ldap

        @Throws(Exception::class)
        private fun createServerSet(context: SharedContext, config: LdapUserDBConfig): ServerSet {
            val ldapUri = URI.create(config.connectionUrl)
            val factory: SocketFactory?
            val defaultPort: Int
            when (ldapUri.scheme.lowercase()) {
                "ldap" -> {
                    factory = null
                    defaultPort = 389
                }
                "ldaps" -> {
                    factory = createSslFactory(context, config)
                    defaultPort = 636
                }
                else -> throw IllegalStateException("Unknown ldap scheme: " + ldapUri.scheme)
            }
            val ldapHost = ldapUri.host
            val ldapPort = if (ldapUri.port > 0) ldapUri.port else defaultPort
            return SingleServerSet(ldapHost, ldapPort, factory)
        }

        private fun createFakeMailSuffix(config: LdapUserDBConfig): String? {
            val suffix = config.fakeMailSuffix
            if (suffix.isEmpty()) {
                return null
            }
            return if (suffix.indexOf('@') < 0) "@$suffix" else suffix
        }

        @Throws(Exception::class)
        private fun createSslFactory(context: SharedContext, config: LdapUserDBConfig): SocketFactory {
            val certPem = config.ldapCertPem
            if (certPem == null) {
                log.info("LDAP certificate not defined. Using JVM default SSL context")
                return SSLContext.getDefault().socketFactory
            }
            val certFile = ConfigHelper.joinPath(context.basePath, certPem)
            log.info("Loading LDAP certificate from: {}", certFile)
            val trustManagers = createTrustManagers(certFile)
            val sslUtil = SSLUtil(trustManagers)
            return sslUtil.createSSLSocketFactory()
        }

        @Throws(Exception::class)
        private fun createTrustManagers(certFile: Path): Array<TrustManager> {
            val certificate = loadCertificate(certFile)
            val keyStore = assembleKeyStore(certificate)
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(keyStore)
            return factory.trustManagers
        }

        @Throws(Exception::class)
        fun loadCertificate(certFile: Path): X509Certificate {
            var certificate: X509Certificate
            val certificateFactory = CertificateFactory.getInstance("X.509")
            Files.newInputStream(certFile).use { `is` -> certificate = certificateFactory.generateCertificate(`is`) as X509Certificate }
            return certificate
        }

        @Throws(Exception::class)
        private fun assembleKeyStore(certificate: X509Certificate): KeyStore {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null)
            keyStore.setCertificateEntry("alias", certificate)
            return keyStore
        }
    }

    init {
        val ldapUri = URI.create(config.connectionUrl)
        baseDn = if (ldapUri.path.isEmpty()) "" else ldapUri.path.substring(1)
        val serverSet = createServerSet(context, config)
        val bindRequest = config.bind.createBindRequest()
        pool = LDAPConnectionPool(serverSet, bindRequest, 1, config.maxConnections)
        fakeMailSuffix = createFakeMailSuffix(config)
        this.config = config
    }
}
