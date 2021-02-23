/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.network

import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.bouncycastle.util.Arrays
import ru.bozaro.gitlfs.client.Client
import ru.bozaro.gitlfs.client.exceptions.RequestException
import ru.bozaro.gitlfs.common.LockConflictException
import ru.bozaro.gitlfs.common.VerifyLocksResult
import ru.bozaro.gitlfs.common.data.*
import svnserver.Loggers
import svnserver.StringHelper
import svnserver.auth.User
import svnserver.ext.gitlfs.storage.LfsReader
import svnserver.ext.gitlfs.storage.LfsStorage
import svnserver.ext.gitlfs.storage.LfsWriter
import svnserver.repository.Depth
import svnserver.repository.git.GitBranch
import svnserver.repository.locks.LockDesc
import svnserver.repository.locks.LockTarget
import svnserver.repository.locks.UnlockTarget
import java.io.IOException
import java.util.*

/**
 * HTTP remote storage for LFS files.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
abstract class LfsHttpStorage : LfsStorage {
    @Throws(IOException::class)
    override fun getReader(oid: String, size: Long): LfsReader? {
        return try {
            if (!oid.startsWith(LfsStorage.OID_PREFIX)) return null
            val hash: String = oid.substring(LfsStorage.OID_PREFIX.length)
            val lfsClient = lfsClient(User.anonymous)
            val res = lfsClient.postBatch(BatchReq(Operation.Download, listOf(Meta(hash, size))))
            if (res.objects.isEmpty()) return null
            val item = res.objects[0]
            if (item.error != null) null else LfsHttpReader(lfsClient, item)
        } catch (e: RequestException) {
            log.error("HTTP request error:" + e.message, e)
            throw e
        }
    }

    protected abstract fun lfsClient(user: User): Client
    override fun getWriter(user: User): LfsWriter {
        val lfsClient = lfsClient(user)
        return LfsHttpWriter(lfsClient)
    }

    @Throws(LockConflictException::class, IOException::class)
    override fun lock(user: User, branch: GitBranch?, path: String): LockDesc {
        val ref = if (branch == null) null else Ref(branch.shortBranchName)
        val client = lfsClient(user)
        return LockDesc.toLockDesc(client.lock(LockDesc.toLfsPath(path), ref))
    }

    @Throws(IOException::class)
    override fun unlock(user: User, branch: GitBranch?, breakLock: Boolean, lockId: String): LockDesc? {
        val ref = if (branch == null) null else Ref(branch.shortBranchName)
        val client = lfsClient(user)
        val result = client.unlock(lockId, breakLock, ref)
        return if (result == null) null else LockDesc.toLockDesc(result)
    }

    @Throws(IOException::class)
    override fun getLocks(user: User, branch: GitBranch?, path: String?, lockId: String?): Array<LockDesc> {
        val pathNorm = StringHelper.normalize(path ?: "/")
        val ref = if (branch == null) null else Ref(branch.shortBranchName)
        val locks = lfsClient(user).listLocks(null, lockId, ref)
        val result = ArrayList<LockDesc>()
        for (lock in locks) {
            val lockDesc: LockDesc = LockDesc.toLockDesc(lock)
            if (!StringHelper.isParentPath(pathNorm, lockDesc.path)) continue
            result.add(lockDesc)
        }
        return result.toTypedArray()
    }

    @Throws(IOException::class)
    override fun verifyLocks(user: User, branch: GitBranch?): VerifyLocksResult {
        val ref = if (branch == null) null else Ref(branch.shortBranchName)
        return lfsClient(user).verifyLocks(ref)
    }

    @Throws(IOException::class)
    override fun unlock(user: User, branch: GitBranch?, breakLock: Boolean, targets: Array<UnlockTarget>): Array<LockDesc> {
        val ref = if (branch == null) null else Ref(branch.shortBranchName)
        val client = lfsClient(user)

        // TODO: this is not atomic :( Waiting for batch LFS locking API
        val result = ArrayList<LockDesc>()
        for (target in targets) {
            val lockId: String = if (target.token == null) {
                val locks = getLocks(user, branch, target.path, null as String?)
                if (locks.isNotEmpty() && locks[0].path == target.path) locks[0].token else continue
            } else {
                target.token
            }
            val lock = client.unlock(lockId, breakLock, ref)
            if (lock != null) result.add(LockDesc.toLockDesc(lock))
        }
        return result.toTypedArray()
    }

    @Throws(IOException::class, LockConflictException::class)
    override fun lock(user: User, branch: GitBranch?, comment: String?, stealLock: Boolean, targets: Array<LockTarget>): Array<LockDesc> {
        val ref = if (branch == null) null else Ref(branch.shortBranchName)
        val client = lfsClient(user)

        // TODO: this is not atomic :( Waiting for batch LFS locking API
        val result = ArrayList<LockDesc>()
        for (target in targets) {
            var lock: Lock
            val path: String = LockDesc.toLfsPath(target.path)
            lock = try {
                client.lock(path, ref)
            } catch (e: LockConflictException) {
                if (stealLock) {
                    client.unlock(e.lock, true, ref)
                    client.lock(path, ref)
                } else {
                    throw e
                }
            }
            result.add(LockDesc.toLockDesc(lock))
        }
        return result.toTypedArray()
    }

    override fun cleanupInvalidLocks(branch: GitBranch): Boolean {
        return false
    }

    override fun refreshLocks(user: User, branch: GitBranch, keepLocks: Boolean, lockDescs: Array<LockDesc>) {
        if (keepLocks) {
            // LFS locks are not auto-unlocked upon commit
            return
        }

        // TODO: this is not atomic :( Waiting for batch LFS locking API
        for (lockDesc in lockDescs) {
            try {
                unlock(user, branch, false, lockDesc.token)
            } catch (e: IOException) {
                log.warn("[{}]: {} failed to release lock {}: {}", branch, user.username, lockDesc, e.message, e)
            }
        }
    }

    @Throws(IOException::class)
    override fun getLocks(user: User, branch: GitBranch, path: String, depth: Depth): Iterator<LockDesc> {
        return Arrays.Iterator(getLocks(user, branch, path, null as String?))
    }

    companion object {
        private val log = Loggers.lfs
        fun createHttpClient(): CloseableHttpClient {
            // HttpClient has strange default cookie spec that produces warnings when talking to Gitea
            // See https://issues.apache.org/jira/browse/HTTPCLIENT-1763
            return HttpClientBuilder.create()
                .setDefaultRequestConfig(
                    RequestConfig.custom()
                        .setCookieSpec(CookieSpecs.STANDARD)
                        .build()
                )
                .build()
        }
    }
}
