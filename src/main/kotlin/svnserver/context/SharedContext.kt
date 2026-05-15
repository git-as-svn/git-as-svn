/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.context

import org.mapdb.DB
import svnserver.config.SharedConfig
import java.io.IOException
import java.nio.file.Path
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * Simple context object.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ThreadSafe
class SharedContext private constructor(val basePath: Path, val cacheDB: DB, val realm: String, val stringInterner: (String) -> String) : Context<Shared>(), AutoCloseable {
    @Throws(IOException::class)
    fun ready() {
        for (item in ArrayList(values())) {
            item.ready(this)
        }
    }

    @Throws(Exception::class)
    override fun close() {
        super.close()
        cacheDB.close()
    }

    companion object {
        @Throws(Exception::class)
        fun create(basePath: Path, realm: String, cacheDb: DB, shared: Collection<SharedConfig>, stringInterner: (String) -> String): SharedContext {
            val context = SharedContext(basePath, cacheDb, realm, stringInterner)
            for (config in shared) {
                config.create(context)
            }
            for (item in ArrayList(context.values())) {
                item.init(context)
            }
            return context
        }
    }
}
