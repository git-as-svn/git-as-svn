/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.combine

import org.tmatesoft.svn.core.SVNException
import svnserver.auth.Authenticator
import svnserver.auth.PlainAuthenticator
import svnserver.auth.User
import svnserver.auth.UserDB

/**
 * Complex authentication.
 * Can combine multiple authenticators.
 *
 * @author Artem V. Navrotskiy
 */
class CombineUserDB(private val userDBs: Array<UserDB>) : UserDB {
    private val authenticators: Collection<Authenticator> = setOf(PlainAuthenticator(this))
    override fun authenticators(): Collection<Authenticator> {
        return authenticators
    }

    @Throws(SVNException::class)
    override fun check(username: String, password: String): User? {
        return firstAvailable { userDB: UserDB -> userDB.check(username, password) }
    }

    @Throws(SVNException::class)
    override fun lookupByUserName(username: String): User? {
        return firstAvailable { userDB: UserDB -> userDB.lookupByUserName(username) }
    }

    @Throws(SVNException::class)
    override fun lookupByExternal(external: String): User? {
        return firstAvailable { userDB: UserDB -> userDB.lookupByExternal(external) }
    }

    @Throws(SVNException::class)
    private fun firstAvailable(callback: FirstAvailableCallback): User? {
        for (userDB in userDBs) {
            val user = callback.exec(userDB)
            if (user != null) {
                return user
            }
        }
        return null
    }

    private fun interface FirstAvailableCallback {
        @Throws(SVNException::class)
        fun exec(userDB: UserDB): User?
    }
}
