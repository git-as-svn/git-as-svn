/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth

import org.apache.commons.collections4.trie.PatriciaTrie
import svnserver.UserType

/**
 * Simple user db with clear-text passwords.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class LocalUserDB : UserDB {
    private val users = PatriciaTrie<UserWithPassword>()

    override val authenticators: Collection<Authenticator> = setOf(CramMD5Authenticator { key: String -> users[key] })

    fun add(username: String, password: String, realName: String, email: String?): User? {
        if (users.containsKey(username)) {
            return null
        }
        val user: User = User.create(username, realName, email, username, UserType.Local, null)
        val userWithPassword = UserWithPassword(user, password)
        users[userWithPassword.user.username] = userWithPassword
        return userWithPassword.user
    }

    override fun check(username: String, password: String): User? {
        val userWithPassword: UserWithPassword = users[username] ?: return null
        if (userWithPassword.password != password) return null
        return userWithPassword.user
    }

    override fun lookupByUserName(username: String): User? {
        return users[username]?.user
    }

    override fun lookupByExternal(external: String): User? {
        return lookupByUserName(external)
    }
}
