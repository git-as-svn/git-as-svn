/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth

import org.tmatesoft.svn.core.SVNException
import svnserver.server.SessionContext
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class PlainAuthenticator constructor(private val userDB: UserDB) : Authenticator {
    override val methodName: String
        get() {
            return "PLAIN"
        }

    @Throws(SVNException::class)
    override fun authenticate(context: SessionContext, token: String): User? {
        val decoded: ByteArray = Base64.getMimeDecoder().decode(token)
        val decodedToken = String(decoded, StandardCharsets.US_ASCII)
        val credentials: Array<String> = decodedToken.split("\u0000").toTypedArray()
        if (credentials.size < 3) return null
        val username: String = credentials.get(1)
        val password: String = credentials.get(2)
        return userDB.check(username, password)
    }
}
