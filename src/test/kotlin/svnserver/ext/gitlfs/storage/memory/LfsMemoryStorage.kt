/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.memory

import svnserver.auth.User
import svnserver.ext.gitlfs.storage.LfsReader
import svnserver.ext.gitlfs.storage.LfsStorage
import svnserver.ext.gitlfs.storage.LfsWriter
import svnserver.repository.locks.LocalLockManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap

/**
 * Memory storage for LFS files.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class LfsMemoryStorage : LocalLockManager(ConcurrentSkipListMap()), LfsStorage {
    val files = ConcurrentHashMap<String, ByteArray>()

    override fun getReader(oid: String, size: Long): LfsReader? {
        val content = files[oid] ?: return null
        return LfsMemoryReader(content)
    }

    override fun getWriter(user: User): LfsWriter {
        return LfsMemoryWriter(files)
    }
}
