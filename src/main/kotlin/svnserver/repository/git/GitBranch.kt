/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.RenameDetector
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.TreeFilter
import org.mapdb.HTreeMap
import org.slf4j.Logger
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import svnserver.Loggers
import svnserver.StringHelper
import svnserver.auth.User
import svnserver.repository.VcsCopyFrom
import svnserver.repository.git.cache.CacheChange
import svnserver.repository.git.cache.CacheRevision
import svnserver.repository.locks.LockStorage
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock

class GitBranch(val repository: GitRepository, val shortBranchName: String) {
    val uuid: String
    val gitBranch: String
    private val svnBranch: String

    /**
     * Lock for prevent concurrent pushes.
     */
    private val pushLock: Any = Any()
    private val revisions = ArrayList<GitRevision>()
    private val revisionByDate = TreeMap<Long, GitRevision>()
    private val revisionByHash = HashMap<ObjectId, GitRevision>()
    private val revisionCache: HTreeMap<ObjectId, CacheRevision>
    private val lastUpdatesLock = ReentrantReadWriteLock()
    private val lastUpdates = HashMap<String, IntArray>()
    private val lock = ReentrantReadWriteLock()

    @Throws(SVNException::class)
    fun getRevisionInfo(revision: Int): GitRevision {
        return getRevisionInfoUnsafe(revision) ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision $revision"))
    }

    private fun getRevisionInfoUnsafe(revision: Int): GitRevision? {
        lock.readLock().lock()
        try {
            if (revision >= revisions.size) return null
            return revisions[revision]
        } finally {
            lock.readLock().unlock()
        }
    }

    val latestRevision: GitRevision
        get() {
            lock.readLock().lock()
            try {
                return revisions[revisions.size - 1]
            } finally {
                lock.readLock().unlock()
            }
        }

    @Throws(IOException::class, SVNException::class)
    fun updateRevisions() {
        var gotNewRevisions = false
        while (true) {
            loadRevisions()
            if (!cacheRevisions()) {
                break
            }
            gotNewRevisions = true
        }
        if (gotNewRevisions) {
            val locksChanged: Boolean = repository.wrapLockWrite { lockStorage: LockStorage -> lockStorage.cleanupInvalidLocks(this) }
            if (locksChanged) repository.context.shared.cacheDB.commit()
        }
    }

    /**
     * Load all cached revisions.
     */
    @Throws(IOException::class)
    private fun loadRevisions() {
        // Fast check.
        lock.readLock().lock()
        try {
            val lastRevision: Int = revisions.size - 1
            val lastCommitId: ObjectId
            if (lastRevision >= 0) {
                lastCommitId = revisions[lastRevision].cacheCommit
                val head: Ref = repository.git.exactRef(svnBranch)
                if (head.objectId.equals(lastCommitId)) {
                    return
                }
            }
        } finally {
            lock.readLock().unlock()
        }
        // Real loading.
        lock.writeLock().lock()
        try {
            val lastRevision: Int = revisions.size - 1
            val lastCommitId: ObjectId? = if (lastRevision < 0) null else revisions[lastRevision].cacheCommit
            val head: Ref = repository.git.exactRef(svnBranch)
            val newRevs: MutableList<RevCommit> = ArrayList()
            val revWalk = RevWalk(repository.git)
            var objectId: ObjectId = head.objectId
            while (true) {
                if (objectId.equals(lastCommitId)) {
                    break
                }
                val commit: RevCommit = revWalk.parseCommit(objectId)
                newRevs.add(commit)
                if (commit.parentCount == 0) break
                objectId = commit.getParent(0)
            }
            if (newRevs.isEmpty()) {
                return
            }
            val beginTime: Long = System.currentTimeMillis()
            var processed = 0
            var reportTime: Long = beginTime
            log.info("[{}]: loading cached revision changes: {} revisions", this, newRevs.size)
            for (i in newRevs.indices.reversed()) {
                loadRevisionInfo(newRevs[i])
                processed++
                val currentTime: Long = System.currentTimeMillis()
                if (currentTime - reportTime > REPORT_DELAY) {
                    log.info("[{}]: processed cached revision: {}/{} ({} rev/sec)", this, newRevs.size - i, newRevs.size, 1000.0f * processed / (currentTime - reportTime))
                    reportTime = currentTime
                    processed = 0
                }
            }
            val endTime: Long = System.currentTimeMillis()
            log.info("[{}]: {} cached revision loaded: {} ms", this, newRevs.size, endTime - beginTime)
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * Create cache for new revisions.
     */
    @Throws(IOException::class)
    private fun cacheRevisions(): Boolean {
        // Fast check.
        lock.readLock().lock()
        try {
            val lastRevision: Int = revisions.size - 1
            if (lastRevision >= 0) {
                val lastCommitId: ObjectId? = revisions[lastRevision].gitNewCommit
                val master: Ref? = repository.git.exactRef(gitBranch)
                if ((master == null) || (master.objectId.equals(lastCommitId))) {
                    return false
                }
            }
        } finally {
            lock.readLock().unlock()
        }
        // Real update.
        lock.writeLock().lock()
        try {
            repository.git.newObjectInserter().use { inserter ->
                val master = repository.git.exactRef(gitBranch)
                val newRevs = ArrayList<RevCommit>()
                val revWalk = RevWalk(repository.git)
                var objectId = master.objectId
                while (true) {
                    if (revisionByHash.containsKey(objectId)) {
                        break
                    }
                    val commit: RevCommit = revWalk.parseCommit(objectId)
                    newRevs.add(commit)
                    if (commit.parentCount == 0) break
                    objectId = commit.getParent(0)
                }
                if (newRevs.isNotEmpty()) {
                    val beginTime: Long = System.currentTimeMillis()
                    var processed = 0
                    var reportTime: Long = beginTime
                    log.info("[{}]: Loading revision changes: {} revision", this, newRevs.size)
                    var revisionId: Int = revisions.size
                    var cacheId: ObjectId? = revisions[revisions.size - 1].cacheCommit
                    for (i in newRevs.indices.reversed()) {
                        val revCommit: RevCommit = newRevs[i]
                        cacheId = LayoutHelper.createCacheCommit(inserter, (cacheId)!!, revCommit, revisionId, emptyMap())
                        inserter.flush()
                        processed++
                        val currentTime: Long = System.currentTimeMillis()
                        if (currentTime - reportTime > REPORT_DELAY) {
                            log.info("  processed revision: {} ({} rev/sec)", newRevs.size - i, 1000.0f * processed / (currentTime - reportTime))
                            reportTime = currentTime
                            processed = 0
                            val refUpdate: RefUpdate = repository.git.updateRef(svnBranch)
                            refUpdate.setNewObjectId(cacheId)
                            refUpdate.update()
                        }
                        revisionId++
                    }
                    val endTime: Long = System.currentTimeMillis()
                    log.info("Revision changes loaded: {} ms", endTime - beginTime)
                    val refUpdate: RefUpdate = repository.git.updateRef(svnBranch)
                    refUpdate.setNewObjectId(cacheId)
                    refUpdate.update()
                }
                return newRevs.isNotEmpty()
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    @Throws(IOException::class)
    private fun loadRevisionInfo(commit: RevCommit) {
        val reader: ObjectReader = repository.git.newObjectReader()
        val cacheRevision: CacheRevision = loadCacheRevision(reader, commit, revisions.size)
        val revisionId: Int = revisions.size
        val copyFroms: MutableMap<String, VcsCopyFrom> = HashMap()
        for (entry: Map.Entry<String, String> in cacheRevision.getRenames().entries) {
            copyFroms[entry.key] = VcsCopyFrom(revisionId - 1, entry.value)
        }
        val oldCommit: RevCommit? = if (revisions.isEmpty()) null else revisions[revisions.size - 1].gitNewCommit
        val svnCommit: RevCommit? = if (cacheRevision.gitCommitId != null) RevWalk(reader).parseCommit(cacheRevision.gitCommitId) else null
        try {
            lastUpdatesLock.writeLock().lock()
            for (entry: Map.Entry<String, CacheChange> in cacheRevision.getFileChange().entries) {
                lastUpdates.compute(entry.key) { _, list ->
                    val markNoFile: Boolean = entry.value.newFile == null
                    val prevLen: Int = list?.size ?: 0
                    val newLen: Int = prevLen + 1 + (if (markNoFile) 1 else 0)
                    val result: IntArray = if (list == null) IntArray(newLen) else Arrays.copyOf(list, newLen)
                    result[prevLen] = revisionId
                    if (markNoFile) {
                        result[prevLen + 1] = MARK_NO_FILE
                    }
                    result
                }
            }
        } finally {
            lastUpdatesLock.writeLock().unlock()
        }
        val revision = GitRevision(this, commit.id, revisionId, copyFroms, oldCommit, svnCommit, commit.commitTime)
        if (revision.id > 0) {
            if (revisionByDate.isEmpty() || revisionByDate.lastKey() <= revision.date) {
                revisionByDate[revision.date] = revision
            }
        }
        if (svnCommit != null) {
            revisionByHash[svnCommit.id] = revision
        }
        revisions.add(revision)
    }

    @Throws(IOException::class)
    private fun loadCacheRevision(reader: ObjectReader, newCommit: RevCommit, revisionId: Int): CacheRevision {
        val cacheKey: ObjectId = newCommit.copy()
        var result: CacheRevision? = revisionCache[cacheKey]
        if (result == null) {
            val baseCommit: RevCommit? = LayoutHelper.loadOriginalCommit(reader, newCommit)
            val oldTree: GitFile = getSubversionTree(reader, if (newCommit.parentCount > 0) newCommit.getParent(0) else null, revisionId - 1)
            val newTree: GitFile = getSubversionTree(reader, newCommit, revisionId)
            val fileChange: MutableMap<String, CacheChange> = TreeMap()
            for (entry: Map.Entry<String, GitLogEntry> in ChangeHelper.collectChanges(oldTree, newTree, true, repository.context.shared.stringInterner).entries) {
                fileChange[entry.key] = CacheChange(entry.value)
            }
            result = CacheRevision(
                baseCommit,
                collectRename(oldTree, newTree),
                fileChange
            )
            revisionCache[cacheKey] = result
        }
        return result
    }

    @Throws(IOException::class)
    private fun getSubversionTree(reader: ObjectReader, commit: RevCommit?, revisionId: Int): GitFile {
        val revCommit: RevCommit = LayoutHelper.loadOriginalCommit(reader, commit) ?: return GitFileEmptyTree(this, "", revisionId - 1)
        return GitFileTreeEntry.create(this, revCommit.tree, revisionId)
    }

    @Throws(IOException::class)
    private fun collectRename(oldTree: GitFile, newTree: GitFile): Map<String, String> {
        if (!repository.hasRenameDetection()) {
            return emptyMap()
        }
        val oldTreeId: GitObject<ObjectId>? = oldTree.objectId
        val newTreeId: GitObject<ObjectId>? = newTree.objectId
        if ((oldTreeId == null) || (newTreeId == null) || !Objects.equals(oldTreeId.repo, newTreeId.repo)) {
            return emptyMap()
        }
        val tw = TreeWalk(repository.git)
        tw.isRecursive = true
        tw.filter = TreeFilter.ANY_DIFF
        tw.addTree(oldTreeId.`object`)
        tw.addTree(newTreeId.`object`)
        val rd = RenameDetector(repository.git)
        rd.addAll(DiffEntry.scan(tw))
        val result = HashMap<String, String>()
        for (diff in rd.compute(tw.objectReader, null)) {
            if (diff.score >= rd.renameScore) {
                result[StringHelper.normalize(diff.newPath)] = StringHelper.normalize(diff.oldPath)
            }
        }
        return result
    }

    fun getRevisionByDate(dateTime: Long): GitRevision {
        lock.readLock().lock()
        try {
            val entry: Map.Entry<Long, GitRevision>? = revisionByDate.floorEntry(dateTime)
            if (entry != null) {
                return entry.value
            }
            return revisions[0]
        } finally {
            lock.readLock().unlock()
        }
    }

    fun sureRevisionInfo(revision: Int): GitRevision {
        return getRevisionInfoUnsafe(revision) ?: throw IllegalStateException("No such revision $revision")
    }

    @Throws(SVNException::class)
    fun getRevision(revisionId: ObjectId): GitRevision {
        lock.readLock().lock()
        try {
            return revisionByHash[revisionId] ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision " + revisionId.name()))
        } finally {
            lock.readLock().unlock()
        }
    }

    fun getLastChange(nodePath: String, beforeRevision: Int): Int? {
        if (nodePath.isEmpty()) return beforeRevision
        try {
            lastUpdatesLock.readLock().lock()
            val revs: IntArray? = lastUpdates[nodePath]
            if (revs != null) {
                var prev = 0
                for (i in revs.indices.reversed()) {
                    val rev: Int = revs[i]
                    if ((rev >= 0) && (rev <= beforeRevision)) {
                        if (prev == MARK_NO_FILE) {
                            return null
                        }
                        return rev
                    }
                    prev = rev
                }
            }
        } finally {
            lastUpdatesLock.readLock().unlock()
        }
        return null
    }

    @Throws(SVNException::class)
    fun createWriter(user: User): GitWriter {
        if (user.email == null || user.email.isEmpty()) {
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Users with undefined email can't create commits"))
        }
        return GitWriter(this, repository.pusher, pushLock, user)
    }

    override fun toString(): String {
        return repository.context.name + "@" + shortBranchName
    }

    companion object {
        private const val revisionCacheVersion: Int = 2
        private const val REPORT_DELAY: Int = 2500
        private const val MARK_NO_FILE: Int = -1
        private val log: Logger = Loggers.git

        @Throws(IOException::class)
        private fun loadRepositoryId(repository: Repository, ref: Ref): String {
            var oid: ObjectId? = ref.objectId
            val revWalk = RevWalk(repository)
            while (true) {
                val revCommit: RevCommit = revWalk.parseCommit(oid)
                if (revCommit.parentCount == 0) {
                    return LayoutHelper.loadRepositoryId(repository.newObjectReader(), oid)
                }
                oid = revCommit.getParent(0)
            }
        }
    }

    init {
        val svnBranchRef: Ref = LayoutHelper.initRepository(repository.git, shortBranchName)
        svnBranch = svnBranchRef.name
        gitBranch = Constants.R_HEADS + shortBranchName
        val repositoryId: String = loadRepositoryId(repository.git, svnBranchRef)
        uuid = UUID.nameUUIDFromBytes(
            String.format("%s\u0000%s\u0000%s", repositoryId, gitBranch, repository.format.revision).toByteArray(StandardCharsets.UTF_8)
        ).toString()
        val revisionCacheName: String = String.format(
            "cache-revision.%s.%s.%s.v%s.%s", repository.context.name, gitBranch, if (repository.hasRenameDetection()) 1 else 0, revisionCacheVersion, repository.format.revision
        )
        revisionCache = repository.context.shared.cacheDB.hashMap<ObjectId, CacheRevision>(
            revisionCacheName,
            ObjectIdSerializer.instance,
            CacheRevisionSerializer.instance
        ).createOrOpen()
    }
}
