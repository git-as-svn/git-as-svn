/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.locks

import com.google.common.base.Strings
import org.mapdb.Serializer
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import ru.bozaro.gitlfs.common.LockConflictException
import ru.bozaro.gitlfs.common.VerifyLocksResult
import ru.bozaro.gitlfs.common.data.Lock
import svnserver.StringHelper
import svnserver.auth.User
import svnserver.context.LocalContext
import svnserver.repository.Depth
import svnserver.repository.git.GitBranch
import svnserver.repository.git.GitFile
import svnserver.repository.git.GitRevision
import java.io.IOException
import java.util.*

/**
 * Lock manager.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
open class LocalLockManager(val locks: SortedMap<String, LockDesc>) : LockStorage {
    @Throws(LockConflictException::class, IOException::class, SVNException::class)
    override fun lock(user: User, branch: GitBranch?, path: String): LockDesc {
        val lock: LockDesc = tryCreateLock(user, null, false, null, path, -1)
        locks[lock.path] = lock
        return lock
    }

    @Throws(LockConflictException::class)
    override fun unlock(user: User, branch: GitBranch?, breakLock: Boolean, lockId: String): LockDesc? {
        var result: LockDesc? = null
        val it = locks.entries.iterator()
        while (it.hasNext()) {
            val lock = it.next()
            if (lockId != lock.value.token) {
                continue
            }
            if (!breakLock && user.username != lock.value.owner) throw LockConflictException(LockDesc.toLock(lock.value))
            result = lock.value
            it.remove()
            break
        }
        return result
    }

    override fun getLocks(user: User, branch: GitBranch?, path: String?, lockId: String?): Array<LockDesc> {
        val pathNormalized = StringHelper.normalize(path ?: "/")
        val result = ArrayList<LockDesc>()
        for ((_, lockDesc) in locks) {
            if ((branch != null) && (lockDesc.branch != null) && branch.shortBranchName != lockDesc.branch) continue
            if (!StringHelper.isParentPath(pathNormalized, lockDesc.path)) continue
            if (!Strings.isNullOrEmpty(lockId) && lockDesc.token != lockId) continue
            result.add(lockDesc)
        }
        return result.toTypedArray()
    }

    override fun verifyLocks(user: User, branch: GitBranch?): VerifyLocksResult {
        val ourLocks = ArrayList<Lock>()
        val theirLocks = ArrayList<Lock>()
        for (lock: LockDesc in getLocks(user, branch, null, null as String?)) {
            val list = if (user.username == lock.owner) ourLocks else theirLocks
            list.add(LockDesc.toLock(lock))
        }
        return VerifyLocksResult(ourLocks, theirLocks)
    }

    @Throws(LockConflictException::class, SVNException::class)
    override fun unlock(user: User, branch: GitBranch?, breakLock: Boolean, targets: Array<UnlockTarget>): Array<LockDesc> {
        val result = ArrayList<LockDesc>()
        for (target: UnlockTarget in targets) {
            val path: String = target.path
            val token: String? = target.token
            val lock: LockDesc = locks[path] ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_LOCK, path))
            if (!breakLock && (lock.token != token || user.username != lock.owner)) throw LockConflictException(LockDesc.toLock(lock))
        }
        for (target: UnlockTarget in targets)
            result.add(locks.remove(target.path)!!)
        return result.toTypedArray()
    }

    @Throws(LockConflictException::class, IOException::class, SVNException::class)
    override fun lock(user: User, branch: GitBranch?, comment: String?, stealLock: Boolean, targets: Array<LockTarget>): Array<LockDesc> {
        var result: Array<LockDesc> = emptyArray()
        if (targets.isNotEmpty()) {
            // Create new locks list.
            result = targets.map { target: LockTarget ->
                val path: String = target.path
                val targetRev: Int = target.rev
                tryCreateLock(user, comment, stealLock, branch, path, targetRev)
            }.toTypedArray()

            // Add locks.
            for (lockDesc: LockDesc in result) locks[lockDesc.path] = lockDesc
        }
        return result
    }

    @Throws(IOException::class)
    override fun cleanupInvalidLocks(branch: GitBranch): Boolean {
        var changed = false
        val revision: GitRevision = branch.latestRevision
        val iter = locks.entries.iterator()
        while (iter.hasNext()) {
            val item: LockDesc = iter.next().value
            if (branch.shortBranchName != item.branch) continue
            val file = revision.getFile(item.path)
            if ((file == null) || file.isDirectory || file.contentHash != item.hash) {
                iter.remove()
                changed = true
            }
        }
        return changed
    }

    @Throws(IOException::class)
    override fun refreshLocks(user: User, branch: GitBranch, keepLocks: Boolean, lockDescs: Array<LockDesc>) {
        if (!keepLocks) return
        val revision: GitRevision = branch.latestRevision
        for (lockDesc: LockDesc in lockDescs) {
            val pathKey: String = lockDesc.path
            if (!locks.containsKey(pathKey)) {
                val file: GitFile? = revision.getFile(lockDesc.path)
                if (file != null && !file.isDirectory) {
                    locks[pathKey] = LockDesc(lockDesc.path, branch.shortBranchName, file.contentHash, lockDesc.token, lockDesc.owner, lockDesc.comment, lockDesc.created)
                }
            }
        }
    }

    @Throws(SVNException::class)
    override fun getLocks(user: User, branch: GitBranch, path: String, depth: Depth): Iterator<LockDesc> {
        return depth.visit(TreeMapLockDepthVisitor(locks, StringHelper.normalize(path)))
    }

    @Throws(IOException::class, SVNException::class, LockConflictException::class)
    private fun tryCreateLock(
        user: User,
        comment: String?,
        stealLock: Boolean,
        branch: GitBranch?,
        path: String,
        targetRev: Int
    ): LockDesc {
        val pathNormalized = StringHelper.normalize(path)
        val hash: String? = if (branch != null) {
            val revision: GitRevision = branch.latestRevision
            val file: GitFile = revision.getFile(pathNormalized) ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, pathNormalized))
            if (file.isDirectory) throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, pathNormalized))
            if (targetRev >= 0 && targetRev < file.lastChange.id) throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, pathNormalized))
            file.contentHash
        } else {
            null
        }
        val currentLock: LockDesc? = locks[pathNormalized]
        if (!stealLock && currentLock != null) throw LockConflictException(LockDesc.toLock(currentLock))
        return LockDesc(pathNormalized, branch, hash, createLockId(), user.username, comment, System.currentTimeMillis())
    }

    companion object {
        private const val lockDescCacheVersion: Int = 3
        fun getPersistentStorage(context: LocalContext): SortedMap<String, LockDesc> {
            val lockCacheName: String = String.format("locks.%s.%s", context.name, lockDescCacheVersion)
            return context.shared.cacheDB.treeMap<String, LockDesc>(
                lockCacheName, Serializer.STRING, LockDescSerializer.instance
            ).createOrOpen()
        }

        private fun createLockId(): String {
            return UUID.randomUUID().toString()
        }
    }

    init {

        // Cleanup locks that were stored with bogus versions of git-as-svn that stored paths without leading slash
        locks.keys.removeIf { s: String -> !s.startsWith("/") }
    }
}
