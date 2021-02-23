/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.keys

import org.tmatesoft.svn.core.SVNException
import svnserver.auth.Authenticator
import svnserver.auth.User
import svnserver.auth.UserDB
import svnserver.server.SessionContext
import java.nio.charset.StandardCharsets
import java.util.*

class KeyAuthenticator internal constructor(
    private val userDB: UserDB,
    private val secretToken: String
) : Authenticator {
    override val methodName: String
        get() = "KEY-AUTHENTICATOR"

    @Throws(SVNException::class)
    override fun authenticate(context: SessionContext, token: String): User? {
        val decodedToken = String(Base64.getDecoder().decode(token.trim { it <= ' ' }), StandardCharsets.US_ASCII)
        val credentials = decodedToken.split("\u0000").toTypedArray()
        if (credentials.size < 3) return null
        val clientSecretToken = credentials[1]
        if (clientSecretToken.trim { it <= ' ' } == secretToken) {
            val username = credentials[2]
            return userDB.lookupByExternal(username)
        }
        return null
    }
}
