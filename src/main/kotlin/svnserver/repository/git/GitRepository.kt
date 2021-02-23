/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import com.sun.nio.sctp.InvalidStreamException
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.mapdb.DB
import org.mapdb.HTreeMap
import org.mapdb.Serializer
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil
import svnserver.StringHelper
import svnserver.context.LocalContext
import svnserver.context.SharedContext
import svnserver.repository.SvnForbiddenException
import svnserver.repository.VcsSupplier
import svnserver.repository.git.filter.GitFilter
import svnserver.repository.git.filter.GitFilters
import svnserver.repository.git.prop.GitProperty
import svnserver.repository.git.prop.GitPropertyFactory
import svnserver.repository.git.prop.PropertyMapping
import svnserver.repository.git.push.GitPusher
import svnserver.repository.locks.LockStorage
import svnserver.repository.locks.LockWorker
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Implementation for Git repository.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitRepository constructor(
    val context: LocalContext,
    git: Repository,
    pusher: GitPusher,
    branches: Set<String>,
    renameDetection: Boolean,
    lockStorage: LockStorage,
    filters: GitFilters,
    val emptyDirs: EmptyDirsSupport
) : AutoCloseable, BranchProvider {
    val git: Repository
    val pusher: GitPusher
    private val binaryCache: HTreeMap<String, Boolean>
    private val gitFilters: GitFilters
    private val directoryPropertyCache = ConcurrentHashMap<ObjectId, Array<GitProperty>>()
    private val filePropertyCache = ConcurrentHashMap<ObjectId, Array<GitProperty>>()
    private val renameDetection: Boolean
    private val lockManagerRwLock = ReentrantReadWriteLock()
    private val lockStorage: LockStorage
    private val db: DB
    override val branches = TreeMap<String, GitBranch>()
    fun hasRenameDetection(): Boolean {
        return renameDetection
    }

    override fun close() {
        context.shared.sure(GitSubmodules::class.java).unregister(git)
    }

    @Throws(SVNException::class, IOException::class)
    fun <T> wrapLockWrite(work: LockWorker<T>): T {
        val result: T = wrapLock(lockManagerRwLock.writeLock(), work)
        db.commit()
        return result
    }

    @Throws(IOException::class, SVNException::class)
    private fun <T> wrapLock(lock: Lock, work: LockWorker<T>): T {
        lock.lock()
        try {
            return work.exec(lockStorage)
        } finally {
            lock.unlock()
        }
    }

    @Throws(IOException::class)
    fun collectProperties(treeEntry: GitTreeEntry, entryProvider: VcsSupplier<Iterable<GitTreeEntry>>): Array<GitProperty> {
        if (treeEntry.fileMode.objectType == Constants.OBJ_BLOB) return emptyArray()
        var props = directoryPropertyCache[treeEntry.objectId.`object`]
        if (props == null) {
            val propList = ArrayList<GitProperty>()
            try {
                for (entry in entryProvider.get()) {
                    val parseProps = parseGitProperty(entry.fileName, entry.objectId)
                    if (parseProps.isNotEmpty()) {
                        propList.addAll(parseProps)
                    }
                }
            } catch (ignored: SvnForbiddenException) {
            }
            props = propList.toTypedArray()
            directoryPropertyCache[treeEntry.objectId.`object`] = props
        }
        return props
    }

    @Throws(IOException::class)
    private fun parseGitProperty(fileName: String, objectId: GitObject<ObjectId>): Array<GitProperty> {
        val factory: GitPropertyFactory = PropertyMapping.getFactory(fileName) ?: return emptyArray()
        return cachedParseGitProperty(objectId, factory)
    }

    @Throws(IOException::class)
    private fun cachedParseGitProperty(objectId: GitObject<ObjectId>, factory: GitPropertyFactory): Array<GitProperty> {
        var property: Array<GitProperty>? = filePropertyCache[objectId.`object`]
        if (property == null) {
            objectId.repo.newObjectReader().use { reader -> reader.open(objectId.`object`).openStream().use { stream -> property = factory.create(stream) } }
            if (property!!.isEmpty()) property = emptyArray()
            filePropertyCache[objectId.`object`] = property!!
        }
        return property!!
    }

    fun getFilter(fileMode: FileMode, props: Array<GitProperty>): GitFilter {
        if (fileMode.objectType != Constants.OBJ_BLOB) return gitFilters.raw
        if (fileMode === FileMode.SYMLINK) return gitFilters.link
        for (i in props.indices.reversed()) {
            val filterName: String = props[i].filterName ?: continue
            return gitFilters[filterName] ?: throw InvalidStreamException("Unknown filter requested: $filterName")
        }
        return gitFilters.raw
    }

    @Throws(IOException::class)
    fun isObjectBinary(filter: GitFilter?, objectId: GitObject<out ObjectId>?): Boolean {
        if (objectId == null || filter == null) return false
        val key: String = filter.name + " " + objectId.`object`.name()
        var result: Boolean? = (binaryCache[key])
        if (result == null) {
            filter.inputStream(objectId).use { stream -> result = SVNFileUtil.detectMimeType(stream) != null }
            binaryCache.putIfAbsent(key, result)
        }
        return result!!
    }

    @Throws(IOException::class)
    fun loadTree(tree: GitTreeEntry?): Iterable<GitTreeEntry> {
        val treeId = getTreeObject(tree) ?: return emptyList()
        // Loading tree.
        val result = ArrayList<GitTreeEntry>()
        val repo = treeId.repo
        val treeParser = CanonicalTreeParser(emptyBytes, repo.newObjectReader(), treeId.`object`)
        while (!treeParser.eof()) {
            result.add(
                GitTreeEntry(
                    treeParser.entryFileMode,
                    GitObject(repo, treeParser.entryObjectId),
                    treeParser.entryPathString
                )
            )
            treeParser.next()
        }
        return result
    }

    @Throws(IOException::class)
    private fun getTreeObject(tree: GitTreeEntry?): GitObject<ObjectId>? {
        if (tree == null) {
            return null
        }
        // Get tree object
        if ((tree.fileMode == FileMode.TREE)) {
            return tree.objectId
        }
        return if ((tree.fileMode == FileMode.GITLINK)) {
            val linkedCommit: GitObject<RevCommit> = loadLinkedCommit(tree.objectId.`object`) ?: throw SvnForbiddenException()
            GitObject(linkedCommit.repo, linkedCommit.`object`.tree)
        } else {
            null
        }
    }

    @Throws(IOException::class)
    private fun loadLinkedCommit(objectId: ObjectId): GitObject<RevCommit>? {
        return context.shared.sure(GitSubmodules::class.java).findCommit(objectId)
    }

    @Throws(SVNException::class, IOException::class)
    fun <T> wrapLockRead(work: LockWorker<T>): T {
        return wrapLock(lockManagerRwLock.readLock(), work)
    }

    companion object {
        val emptyBytes: ByteArray = byteArrayOf()

        @Throws(IOException::class)
        fun loadContent(reader: ObjectReader, objectId: ObjectId): String {
            val bytes: ByteArray = reader.open(objectId).cachedBytes
            return String(bytes, StandardCharsets.UTF_8)
        }
    }

    init {
        val shared: SharedContext = context.shared
        shared.getOrCreate(GitSubmodules::class.java) { GitSubmodules() }.register(git)
        this.git = git
        db = shared.cacheDB
        binaryCache = db.hashMap("cache.binary", Serializer.STRING, Serializer.BOOLEAN).createOrOpen()
        this.pusher = pusher
        this.renameDetection = renameDetection
        this.lockStorage = lockStorage
        gitFilters = filters
        for (branch: String in branches) this.branches[StringHelper.normalizeDir(branch)] = GitBranch(this, branch)
    }
}
