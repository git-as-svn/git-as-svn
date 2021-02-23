/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.combine

import svnserver.auth.UserDB
import svnserver.config.UserDBConfig
import svnserver.context.SharedContext

/**
 * Complex authentication.
 * Can combine multiple authenticators.
 *
 * @author Artem V. Navrotskiy
 */
class CombineUserDBConfig : UserDBConfig {
    /**
     * Combined user databases.
     */
    private var items: Array<UserDBConfig> = emptyArray()

    @Throws(Exception::class)
    override fun create(context: SharedContext): UserDB {
        return CombineUserDB(items.map { item -> item.create(context) }.toTypedArray())
    }
}
