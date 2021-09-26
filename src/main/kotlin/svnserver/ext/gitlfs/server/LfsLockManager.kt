/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server

import jakarta.servlet.http.HttpServletRequest
import org.tmatesoft.svn.core.SVNException
import ru.bozaro.gitlfs.common.LockConflictException
import ru.bozaro.gitlfs.common.VerifyLocksResult
import ru.bozaro.gitlfs.common.data.Lock
import ru.bozaro.gitlfs.common.data.Ref
import ru.bozaro.gitlfs.server.ForbiddenError
import ru.bozaro.gitlfs.server.LockManager
import ru.bozaro.gitlfs.server.LockManager.LockRead
import ru.bozaro.gitlfs.server.LockManager.LockWrite
import ru.bozaro.gitlfs.server.UnauthorizedError
import svnserver.auth.User
import svnserver.repository.locks.LockDesc
import java.io.IOException
import java.util.*
import java.util.stream.Collectors

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
internal class LfsLockManager(private val lfsContentManager: LfsContentManager) : LockManager {
    @Throws(IOException::class, ForbiddenError::class, UnauthorizedError::class)
    override fun checkDownloadAccess(request: HttpServletRequest): LockRead {
        val user = lfsContentManager.checkDownload(request)
        return LockWriteImpl(user)
    }

    @Throws(IOException::class, ForbiddenError::class, UnauthorizedError::class)
    override fun checkUploadAccess(request: HttpServletRequest): LockWrite {
        val user = lfsContentManager.checkUpload(request)
        return LockWriteImpl(user)
    }

    private inner class LockWriteImpl(private val user: User) : LockWrite {
        @Throws(LockConflictException::class, IOException::class)
        override fun lock(path: String, ref: Ref?): Lock {
            val lock: LockDesc = try {
                lfsContentManager.storage.lock(user, null, path)
            } catch (e: SVNException) {
                throw IOException(e)
            }
            return LockDesc.toLock(lock)
        }

        @Throws(LockConflictException::class, IOException::class)
        override fun unlock(lockId: String, force: Boolean, ref: Ref?): Lock? {
            val lock: LockDesc? = try {
                lfsContentManager.storage.unlock(user, null, force, lockId)
            } catch (e: SVNException) {
                throw IOException(e)
            }
            return if (lock == null) null else LockDesc.toLock(lock)
        }

        @Throws(IOException::class)
        override fun verifyLocks(ref: Ref?): VerifyLocksResult {
            return lfsContentManager.storage.verifyLocks(user, null)
        }

        @Throws(IOException::class)
        override fun getLocks(path: String?, lockId: String?, ref: Ref?): List<Lock> {
            val locks: Array<LockDesc> = lfsContentManager.storage.getLocks(user, null, path, lockId)
            return Arrays.stream(locks).map { lockDesc: LockDesc -> LockDesc.toLock(lockDesc) }.collect(Collectors.toList())
        }
    }
}
