/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.ldap.config

import com.unboundid.ldap.sdk.Filter
import com.unboundid.ldap.sdk.LDAPException
import svnserver.auth.UserDB
import svnserver.auth.ldap.LdapUserDB
import svnserver.config.UserDBConfig
import svnserver.context.SharedContext
import java.nio.file.Path

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class LdapUserDBConfig(
    /**
     * This is a URL whose format is defined by the JNDI provider.
     * It is usually an LDAP URL that specifies the domain name of the directory server to connect to,
     * and optionally the port number and distinguished name (DN) of the required root naming context.
     */
    var connectionUrl: String = "ldap://localhost:389/ou=groups,dc=mycompany,dc=com",
    /**
     * Bind configuration.
     */
    var bind: LdapBind = LdapBindANONYMOUS.instance,
    /**
     * Common part of search filter.
     */
    private var searchFilter: String = "",
    /**
     * LDAP attribute, containing user login.
     */
    var loginAttribute: String = "sAMAccountName",
    /**
     * LDAP attribute, containing user name.
     */
    var nameAttribute: String = "name",
    /**
     * LDAP attribute, containing user email.
     */
    var emailAttribute: String = "mail",
    ldapCertPem: Path? = null,
    /**
     * Maximum LDAP connections.
     */
    var maxConnections: Int = 10,
    /**
     * Email addresses suffix for users without LDAP email.
     * If empty - don't generate emails.
     */
    var fakeMailSuffix: String = ""
) : UserDBConfig {

    /**
     * Certificate for validation LDAP server with SSL connection.
     */
    var ldapCertPem: String? = ldapCertPem?.toString()

    @Throws(Exception::class)
    override fun create(context: SharedContext): UserDB {
        return LdapUserDB(context, this)
    }

    @Throws(LDAPException::class)
    fun createSearchFilter(username: String): Filter {
        val filter = Filter.createEqualityFilter(loginAttribute, username)
        return if (searchFilter.isEmpty()) filter else Filter.createANDFilter(Filter.create(searchFilter), filter)
    }
}
