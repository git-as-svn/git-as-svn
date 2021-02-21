/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config

import org.mapdb.DB
import org.mapdb.DBException
import org.mapdb.DBMaker.fileDB
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Persistent cache config.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class PersistentCacheConfig : CacheConfig {
    private var path: String = "git-as-svn.mapdb"
    private var enableTransactions: Boolean = true

    @Throws(IOException::class)
    override fun createCache(basePath: Path): DB {
        val cacheBase: Path = ConfigHelper.joinPath(basePath, path)
        Files.createDirectories(cacheBase.parent)
        try {
            val maker = fileDB(cacheBase.toFile())
                .closeOnJvmShutdown()
                .fileMmapEnableIfSupported()
            if (enableTransactions) maker.transactionEnable()
            return maker
                .make()
        } catch (e: DBException) {
            throw DBException(String.format("Failed to open %s: %s", cacheBase, e.message), e)
        }
    }
}
