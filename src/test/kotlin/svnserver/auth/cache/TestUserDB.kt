/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.cache

import svnserver.auth.Authenticator
import svnserver.auth.PlainAuthenticator
import svnserver.auth.User
import svnserver.auth.UserDB

/**
 * Testing UserDB implementation.
 *
 * @author Artem V. Navrotskiy
 */
internal class TestUserDB(vararg users: User) : UserDB {
    private val users = arrayOf(*users)
    private val report = StringBuilder()
    override fun check(username: String, password: String): User? {
        log("check: $username, $password")
        if (password == password(username)) {
            for (user in users) {
                if (user.username == username) {
                    return user
                }
            }
        }
        return null
    }

    private fun log(message: String) {
        if (report.isNotEmpty()) report.append('\n')
        report.append(message)
    }

    fun password(username: String): String {
        return "~~~$username~~~"
    }

    override fun lookupByUserName(username: String): User? {
        log("lookupByUserName: $username")
        for (user in users) {
            if (user.username == username) {
                return user
            }
        }
        return null
    }

    override fun lookupByExternal(external: String): User? {
        log("lookupByExternal: $external")
        for (user in users) {
            if (user.externalId == external) {
                return user
            }
        }
        return null
    }

    fun report(): String {
        return report.toString()
    }

    override val authenticators: Collection<Authenticator> = listOf(PlainAuthenticator(this))
}
