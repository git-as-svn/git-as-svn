/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.cache

import com.google.common.cache.CacheBuilder
import svnserver.auth.UserDB
import svnserver.config.LocalUserDBConfig
import svnserver.config.UserDBConfig
import svnserver.context.SharedContext
import java.util.concurrent.TimeUnit

/**
 * Authentication cache.
 * Can reduce authentication external service calls.
 *
 * @author Artem V. Navrotskiy
 */
class CacheUserDBConfig : UserDBConfig {
    /**
     * User database.
     */
    private var userDB: UserDBConfig = LocalUserDBConfig()

    /**
     * Maximum cache items.
     */
    private var maximumSize: Long = 10000

    /**
     * Cache item expiration (ms).
     */
    private var expireTimeMs: Long = 15000

    @Throws(Exception::class)
    override fun create(context: SharedContext): UserDB {
        return CacheUserDB(
            userDB.create(context), CacheBuilder.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireTimeMs, TimeUnit.MILLISECONDS)
                .build()
        )
    }
}
