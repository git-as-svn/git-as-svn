/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config

import svnserver.auth.LocalUserDB
import svnserver.auth.UserDB
import svnserver.context.SharedContext

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class LocalUserDBConfig : UserDBConfig {
    private var users: Array<UserEntry> = emptyArray()

    constructor()
    constructor(users: Array<UserEntry>) {
        this.users = users
    }

    override fun create(context: SharedContext): UserDB {
        val result = LocalUserDB()
        for (user: UserEntry in users) result.add(user.username, user.password, user.realName, user.email)
        return result
    }

    class UserEntry {
        var username: String = ""
        var realName: String = ""
        var email: String? = null
        var password: String = ""

        constructor()
        constructor(username: String, realName: String, email: String?, password: String) {
            this.username = username
            this.realName = realName
            this.email = email
            this.password = password
        }
    }
}
