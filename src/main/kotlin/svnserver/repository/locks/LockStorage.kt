/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.locks

import org.tmatesoft.svn.core.SVNException
import ru.bozaro.gitlfs.common.LockConflictException
import ru.bozaro.gitlfs.common.VerifyLocksResult
import svnserver.auth.User
import svnserver.repository.Depth
import svnserver.repository.git.GitBranch
import java.io.IOException

interface LockStorage {
    @Throws(LockConflictException::class, IOException::class, SVNException::class)
    fun lock(user: User, branch: GitBranch?, path: String): LockDesc

    @Throws(LockConflictException::class, IOException::class, SVNException::class)
    fun unlock(user: User, branch: GitBranch?, breakLock: Boolean, lockId: String): LockDesc?

    @Throws(IOException::class)
    fun getLocks(user: User, branch: GitBranch?, path: String?, lockId: String?): Array<LockDesc>

    @Throws(IOException::class)
    fun verifyLocks(user: User, branch: GitBranch?): VerifyLocksResult

    @Throws(LockConflictException::class, IOException::class, SVNException::class)
    fun unlock(user: User, branch: GitBranch?, breakLock: Boolean, targets: Array<UnlockTarget>): Array<LockDesc>

    @Throws(LockConflictException::class, IOException::class, SVNException::class)
    fun lock(user: User, branch: GitBranch?, comment: String?, stealLock: Boolean, targets: Array<LockTarget>): Array<LockDesc>

    @Throws(IOException::class)
    fun cleanupInvalidLocks(branch: GitBranch): Boolean

    @Throws(IOException::class)
    fun refreshLocks(user: User, branch: GitBranch, keepLocks: Boolean, lockDescs: Array<LockDesc>)

    @Throws(IOException::class, SVNException::class)
    fun getLocks(user: User, branch: GitBranch, path: String, depth: Depth): Iterator<LockDesc>
}
