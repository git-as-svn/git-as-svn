/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ldap

import com.unboundid.ldap.listener.InMemoryListenerConfig
import com.unboundid.util.ssl.SSLUtil
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.testng.Assert
import svnserver.auth.ldap.LdapUserDB
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
internal class SslDirectoryServerNet(override val certificatePath: Path, keyFile: Path) : DirectoryServerNet {
    override val listenerConfig: InMemoryListenerConfig
    override val urlSchema: String
        get() = "ldaps"

    override fun toString(): String {
        return urlSchema
    }

    companion object {
        private fun createKeyManagers(certFile: Path, keyFile: Path): Array<KeyManager> {
            val certificate = LdapUserDB.loadCertificate(certFile)
            val key = loadKey(keyFile)
            val keyPassword = "12345".toCharArray()
            val keyStore = assembleKeyStore(certificate, key, keyPassword)
            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagerFactory.init(keyStore, keyPassword)
            return keyManagerFactory.keyManagers
        }

        private fun loadKey(keyFile: Path): PrivateKey {
            val keyPair: PEMKeyPair
            Files.newInputStream(keyFile).use { keyStream -> InputStreamReader(keyStream, StandardCharsets.US_ASCII).use { keyReader -> PEMParser(keyReader).use { parser -> keyPair = parser.readObject() as PEMKeyPair } } }
            Assert.assertNotNull(keyPair)
            val keySpec = PKCS8EncodedKeySpec(keyPair.privateKeyInfo.encoded)
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
        }

        private fun assembleKeyStore(certificate: Certificate, key: PrivateKey, keyPassword: CharArray): KeyStore {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null)
            keyStore.setKeyEntry("key", key, keyPassword, arrayOf(certificate))
            return keyStore
        }
    }

    init {
        val keyManagers = createKeyManagers(certificatePath, keyFile)
        val sslUtil = SSLUtil(keyManagers, null)
        listenerConfig = InMemoryListenerConfig.createLDAPSConfig(urlSchema, sslUtil.createSSLServerSocketFactory())
    }
}
