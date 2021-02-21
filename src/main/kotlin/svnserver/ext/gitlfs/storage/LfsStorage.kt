/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage

import svnserver.auth.User
import svnserver.context.Local
import svnserver.repository.locks.LockStorage
import java.io.IOException

/**
 * GIT LFS storage interface.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
interface LfsStorage : Local, LockStorage {
    /**
     * Create reader for object by SHA-256 hash.
     *
     * @param oid Object hash.
     * @return Object reader or null if object not exists.
     * @throws IOException .
     */
    @Throws(IOException::class)
    fun getReader(oid: String, size: Long): LfsReader?

    /**
     * Create writer for object.
     *
     * @return Object writer.
     * @throws IOException .
     */
    @Throws(IOException::class)
    fun getWriter(user: User): LfsWriter

    companion object {
        const val OID_PREFIX = "sha256:"
    }
}
