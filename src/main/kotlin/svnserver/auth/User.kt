/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth

import svnserver.UserType
import java.util.*

/**
 * User. Just user.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class User constructor(val username: String, val realName: String, val email: String?, val externalId: String?, val isAnonymous: Boolean, val type: UserType, val lfsCredentials: LfsCredentials?) {
    override fun hashCode(): Int {
        var result: Int = username.hashCode()
        result = 31 * result + realName.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (email?.hashCode() ?: 0)
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val user: User = other as User
        return (Objects.equals(externalId, user.externalId)
                && Objects.equals(email, user.email)
                && (username == user.username) && (realName == user.realName) && (type == user.type) && (isAnonymous == user.isAnonymous))
    }

    override fun toString(): String {
        return username
    }

    class LfsCredentials constructor(val username: String, val password: String)
    companion object {
        val anonymous: User = User("\$anonymous", "anonymous", null, null, true, UserType.Local, null)
        fun create(username: String, realName: String, email: String?, externalId: String?, type: UserType, lfsCredentials: LfsCredentials?): User {
            return User(username, realName, email, externalId, false, type, lfsCredentials)
        }
    }
}
