/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.local

import svnserver.Loggers
import svnserver.auth.User
import svnserver.context.LocalContext
import svnserver.ext.gitlfs.LocalLfsConfig.LfsLayout
import svnserver.ext.gitlfs.storage.LfsReader
import svnserver.ext.gitlfs.storage.LfsStorage
import svnserver.ext.gitlfs.storage.LfsWriter
import svnserver.repository.git.GitLocation
import svnserver.repository.locks.LocalLockManager
import svnserver.repository.locks.LockDesc
import java.io.IOException
import java.nio.file.Path
import java.util.*

/**
 * Local directory storage for LFS files.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class LfsLocalStorage(locks: SortedMap<String, LockDesc>, private val layout: LfsLayout, private val dataRoot: Path, private val metaRoot: Path?, compress: Boolean) : LocalLockManager(locks), LfsStorage {
    private val compress: Boolean = compress && metaRoot != null

    @Throws(IOException::class)
    override fun getReader(oid: String, size: Long): LfsReader? {
        return LfsLocalReader.create(layout, dataRoot, metaRoot, oid)
    }

    @Throws(IOException::class)
    override fun getWriter(user: User): LfsWriter {
        return LfsLocalWriter(layout, dataRoot, metaRoot, compress, user)
    }

    companion object {
        const val CREATE_TIME = "create-time"
        const val META_EMAIL = "author-email"
        const val META_USER_NAME = "author-login"
        const val META_REAL_NAME = "author-name"
        private val log = Loggers.lfs
        fun getPath(layout: LfsLayout, root: Path, oid: String, suffix: String): Path? {
            if (!oid.startsWith(LfsStorage.OID_PREFIX)) return null
            val offset: Int = LfsStorage.OID_PREFIX.length
            return root.resolve(layout.getPath(oid.substring(offset)) + suffix)
        }

        fun getMetaRoot(context: LocalContext): Path {
            return context.sure(GitLocation::class.java).fullPath.resolve("lfs/meta")
        }
    }

    init {
        if (compress && metaRoot == null) {
            log.error("Compression not supported for local LFS storage without metadata. Compression is disabled")
        }
    }
}
