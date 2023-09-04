/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config

import org.tmatesoft.svn.core.internal.delta.SVNDeltaCompression
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Top configuration object.
 *
 * @author a.navrotskiy
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class Config {
    var host: String = "0.0.0.0"
    var realm: String = "git-as-svn realm"
    var repositoryMapping: RepositoryMappingConfig = RepositoryListMappingConfig()
    var userDB: UserDBConfig = LocalUserDBConfig()
    var cacheConfig: CacheConfig = PersistentCacheConfig()
    var shared = ArrayList<SharedConfig>()
    var port: Int = 3690
    var reuseAddress: Boolean = false
    var compressionLevel: SVNDeltaCompression = SVNDeltaCompression.LZ4
    var shutdownTimeout: Long = TimeUnit.SECONDS.toMillis(5)
    var parallelIndexing: Boolean = true
    var stringInterning: Boolean = false
    val maxConcurrentConnections = Integer.MAX_VALUE

    constructor()
    constructor(host: String, port: Int) {
        this.host = host
        this.port = port
    }
}
