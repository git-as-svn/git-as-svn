/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ldap

import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import com.unboundid.ldap.sdk.DN
import com.unboundid.ldif.LDIFReader
import svnserver.auth.ldap.config.LdapBind
import svnserver.auth.ldap.config.LdapBindPLAIN
import svnserver.auth.ldap.config.LdapUserDBConfig
import svnserver.config.UserDBConfig
import java.net.URL

/**
 * Embedded LDAP server.
 *
 * @author Artem V. Navrotskiy (bozaro at buzzsoft.ru)
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class EmbeddedDirectoryServer private constructor(dn: String, ldifStream: URL, private val serverNet: DirectoryServerNet) : AutoCloseable {
    private val server: InMemoryDirectoryServer
    private val baseDn: DN = DN(dn)
    override fun close() {
        server.shutDown(true)
    }

    fun createUserConfig(): UserDBConfig {
        val connectionUrl = String.format("%s://localhost:%s/%s", serverNet.urlSchema, server.listenPort, baseDn)
        val ldapBind: LdapBind = LdapBindPLAIN("u:$ADMIN_USERNAME", ADMIN_PASSWORD)
        return LdapUserDBConfig(
            connectionUrl,
            ldapBind,
            "",
            "uid",
            "givenName",
            "mail",
            serverNet.certificatePath,
            3,
            ""
        )
    }

    companion object {
        const val ADMIN_USERNAME = "ldapadmin"
        const val ADMIN_PASSWORD = "123456789012345678901234567890123456789012345678901234567890"
        fun create(serverNet: DirectoryServerNet): EmbeddedDirectoryServer {
            return EmbeddedDirectoryServer("dc=example,dc=com", EmbeddedDirectoryServer::class.java.getResource("ldap.ldif"), serverNet)
        }
    }

    init {
        val config = InMemoryDirectoryServerConfig(dn)
        config.setListenerConfigs(serverNet.listenerConfig)
        server = InMemoryDirectoryServer(config)
        ldifStream.openStream().use { `in` -> server.importFromLDIF(false, LDIFReader(`in`)) }
        server.startListening()
    }
}
