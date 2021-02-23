/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs

import svnserver.config.ConfigHelper
import svnserver.config.SharedConfig
import svnserver.context.LocalContext
import svnserver.context.SharedContext
import svnserver.ext.gitlfs.server.LfsServer
import svnserver.ext.gitlfs.storage.LfsStorage
import svnserver.ext.gitlfs.storage.LfsStorageFactory
import svnserver.ext.gitlfs.storage.local.LfsLocalStorage
import svnserver.repository.locks.LocalLockManager

/**
 * Git LFS configuration file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class LocalLfsConfig @JvmOverloads constructor(private var path: String = "lfs", private var saveMeta: Boolean = true) : SharedConfig, LfsStorageFactory {
    private var tokenExpireSec = DEFAULT_TOKEN_EXPIRE_SEC
    private var tokenEnsureTime = DEFAULT_TOKEN_ENSURE_TIME
    private var compress = true
    private var secretToken = ""
    private var layout = LfsLayout.OneLevel
    override fun create(context: SharedContext) {
        context.add(LfsStorageFactory::class.java, this)
        context.add(LfsServer::class.java, LfsServer(secretToken, tokenExpireSec, tokenEnsureTime))
    }

    override fun createStorage(context: LocalContext): LfsStorage {
        val dataRoot = ConfigHelper.joinPath(context.shared.basePath, path)
        return LfsLocalStorage(
            LocalLockManager.getPersistentStorage(context),
            layout,
            dataRoot,
            if (saveMeta) LfsLocalStorage.getMetaRoot(context) else null,
            compress
        )
    }

    /**
     * Git LFS file layout.
     *
     * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
     */
    enum class LfsLayout {
        OneLevel {
            override fun getPath(hash: String): String {
                return hash.substring(0, 2) + "/" + hash
            }
        },
        TwoLevels {
            override fun getPath(hash: String): String {
                return hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + hash
            }
        },
        GitLab {
            override fun getPath(hash: String): String {
                return hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + hash.substring(4)
            }
        };

        abstract fun getPath(hash: String): String
    }

    companion object {
        // Default client token expiration time.
        const val DEFAULT_TOKEN_EXPIRE_SEC = 3600L

        // Allow batch API request only if token is not expired in token ensure time (part of tokenExpireTime).
        private const val DEFAULT_TOKEN_ENSURE_TIME = 0.5f
    }
}
