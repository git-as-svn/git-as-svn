/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import org.eclipse.jgit.lib.*
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.revwalk.RevWalk
import org.slf4j.Logger
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNProperty
import svnserver.Loggers
import svnserver.ReferenceLink
import svnserver.StringHelper
import svnserver.auth.User
import svnserver.repository.Depth
import svnserver.repository.VcsConsumer
import svnserver.repository.git.prop.PropertyMapping
import svnserver.repository.git.push.GitPusher
import svnserver.repository.locks.LockDesc
import svnserver.repository.locks.LockStorage
import java.io.IOException
import java.util.*
import kotlin.collections.HashSet

/**
 * Git commit writer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitWriter internal constructor(val branch: GitBranch, private val pusher: GitPusher, private val pushLock: Any, private val user: User) : AutoCloseable {
    val inserter: ObjectInserter = branch.repository.git.newObjectInserter()

    @Throws(IOException::class)
    fun createFile(parent: GitEntry, name: String): GitDeltaConsumer {
        return GitDeltaConsumer(this, parent.createChild(name, false, branch.repository.context.shared.stringInterner), null, user)
    }

    @Throws(IOException::class)
    fun modifyFile(parent: GitEntry, name: String, file: GitFile): GitDeltaConsumer {
        return GitDeltaConsumer(this, parent.createChild(name, false, branch.repository.context.shared.stringInterner), file, user)
    }

    @Throws(IOException::class)
    fun createCommitBuilder(lockManager: LockStorage, locks: Map<String, String>): GitCommitBuilder {
        return GitCommitBuilder(lockManager, locks)
    }

    override fun close() {
        inserter.use { }
    }

    private abstract class CommitAction(root: GitFile) {
        private val treeStack: Deque<GitFile>
        val element: GitFile
            get() {
                return treeStack.element()
            }

        @Throws(IOException::class)
        fun openDir(name: String) {
            val file: GitFile = treeStack.element().getEntry(name) ?: throw IllegalStateException("Invalid state: can't find file $name in created commit.")
            treeStack.push(file)
        }

        @Throws(IOException::class)
        abstract fun checkProperties(name: String?, props: Map<String, String>, deltaConsumer: GitDeltaConsumer?)
        fun closeDir() {
            treeStack.pop()
        }

        init {
            treeStack = ArrayDeque()
            treeStack.push(root)
        }
    }

    private class GitFilterMigration(root: GitFile) : CommitAction(root) {
        private var migrateCount: Int = 0

        @Throws(IOException::class)
        override fun checkProperties(name: String?, props: Map<String, String>, deltaConsumer: GitDeltaConsumer?) {
            val dir: GitFile = element
            val node: GitFile = (if (name == null) dir else dir.getEntry(name)) ?: throw IllegalStateException("Invalid state: can't find entry $name in created commit.")
            if (deltaConsumer != null) {
                assert((node.filter != null))
                if (deltaConsumer.migrateFilter(node.filter)) {
                    migrateCount++
                }
            }
        }

        fun done(): Int {
            return migrateCount
        }
    }

    private class GitPropertyValidator(root: GitFile) : CommitAction(root) {
        private val propertyMismatch = TreeMap<String, MutableSet<String>>()
        private var errorCount = 0

        @Throws(IOException::class)
        override fun checkProperties(name: String?, props: Map<String, String>, deltaConsumer: GitDeltaConsumer?) {
            val dir: GitFile = element
            val node: GitFile = (if (name == null) dir else dir.getEntry(name)) ?: throw IllegalStateException("Invalid state: can't find entry $name in created commit.")
            if (deltaConsumer != null) {
                assert((node.filter != null))
                if (node.filter!!.name != deltaConsumer.filterName) {
                    throw IllegalStateException(
                        ("Invalid writer filter:\n" + "Expected: " + node.filter!!.name + "\n" + "Actual: " + deltaConsumer.filterName)
                    )
                }
            }
            val expected: Map<String, String> = node.properties
            if (props != expected) {
                if (errorCount < MAX_PROPERTY_ERRORS) {
                    val delta: StringBuilder = StringBuilder()
                    delta.append("Expected:\n")
                    for (entry in expected.entries) {
                        delta.append("  ").append(entry.key).append(" = \"").append(entry.value).append("\"\n")
                    }
                    delta.append("Actual:\n")
                    for (entry in props.entries) {
                        delta.append("  ").append(entry.key).append(" = \"").append(entry.value).append("\"\n")
                    }
                    propertyMismatch.compute(delta.toString()) { _, _value ->
                        var value = _value
                        if (value == null) {
                            value = HashSet()
                        }
                        value.add(node.fullPath)
                        value
                    }
                    errorCount++
                }
            }
        }

        @Throws(SVNException::class)
        fun done() {
            if (propertyMismatch.isNotEmpty()) {
                val message: StringBuilder = StringBuilder()
                for (entry: Map.Entry<String, Set<String>> in propertyMismatch.entries) {
                    if (message.isNotEmpty()) {
                        message.append("\n")
                    }
                    message.append("Invalid svn properties on files:\n")
                    for (path: String? in entry.value) {
                        message.append("  ").append(path).append("\n")
                    }
                    message.append(entry.key)
                }
                message.append(
                    ("\n" + "----------------\n" + "Subversion properties must be consistent with Git config files:\n")
                )
                for (configFile: String? in PropertyMapping.registeredFiles) {
                    message.append("  ").append(configFile).append('\n')
                }
                message.append(
                    "\n" + "For more detailed information, see:"
                ).append("\n").append(ReferenceLink.InvalidSvnProps.link)
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.REPOS_HOOK_FAILURE, message.toString()))
            }
        }
    }

    inner class GitCommitBuilder internal constructor(private val lockManager: LockStorage, private val locks: Map<String, String>) {
        private val treeStack: Deque<GitTreeUpdate>
        private val revision = branch.latestRevision
        private val commitActions = ArrayList<VcsConsumer<CommitAction>>()

        @get:Throws(IOException::class)
        private val originalTree: Map<String, GitTreeEntry>
            get() {
                val commit: RevCommit = revision.gitNewCommit ?: return TreeMap()
                return branch.repository.loadTree(GitTreeEntry(branch.repository.git, FileMode.TREE, commit.tree, ""))
            }

        @Throws(SVNException::class, IOException::class)
        fun addDir(name: String, sourceDir: GitFile?) {
            val current: GitTreeUpdate = treeStack.element()
            if (current.entries.containsKey(name)) {
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, getFullPath(name)))
            }
            commitActions.add(VcsConsumer { action: CommitAction -> action.openDir(name) })
            treeStack.push(GitTreeUpdate(name, branch.repository.loadTree(sourceDir?.treeEntry)))
        }

        private fun getFullPath(name: String): String {
            val fullPath: StringBuilder = StringBuilder()
            val iter: Iterator<GitTreeUpdate> = treeStack.descendingIterator()
            while (iter.hasNext()) {
                fullPath.append(iter.next().name).append('/')
            }
            fullPath.append(name)
            return fullPath.toString()
        }

        @Throws(SVNException::class, IOException::class)
        fun openDir(name: String) {
            val current: GitTreeUpdate = treeStack.element()
            val originalDir: GitTreeEntry? = current.entries.remove(name)
            if ((originalDir == null) || (originalDir.fileMode != FileMode.TREE)) {
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, getFullPath(name)))
            }
            commitActions.add(VcsConsumer { action: CommitAction -> action.openDir(name) })
            treeStack.push(GitTreeUpdate(name, branch.repository.loadTree(originalDir)))
        }

        fun checkDirProperties(props: Map<String, String>) {
            commitActions.add(VcsConsumer { action: CommitAction -> action.checkProperties(null, props, null) })
        }

        @Throws(SVNException::class, IOException::class)
        fun closeDir() {
            val last: GitTreeUpdate = treeStack.pop()
            val current: GitTreeUpdate = treeStack.element()
            val fullPath: String = getFullPath(last.name)
            if (last.entries.isEmpty()) {
                if (branch.repository.emptyDirs.autoCreateKeepFile()) {
                    val keepFile = GitTreeEntry(
                        branch.repository.git, FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, keepFileContents), keepFileName
                    )
                    last.entries[keepFile.fileName] = keepFile
                } else {
                    throw SVNException(SVNErrorMessage.create(SVNErrorCode.CANCELLED, "Empty directories are not supported: $fullPath"))
                }
            } else if (branch.repository.emptyDirs.autoDeleteKeepFile() && last.entries.containsKey(keepFileName) && (last.entries.size > 1)) {
                // remove keep file if it is not the only file in the directory
                // would be good to also validate the content
                last.entries.remove(keepFileName)
            }
            val subtreeId: ObjectId = last.buildTree(inserter)
            log.debug("Create tree {} for dir: {}", subtreeId.name(), fullPath)
            if (current.entries.put(last.name, GitTreeEntry(FileMode.TREE, GitObject(branch.repository.git, subtreeId), last.name)) != null) {
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_ALREADY_EXISTS, fullPath))
            }
            commitActions.add(VcsConsumer { it.closeDir() })
        }

        @Throws(SVNException::class, IOException::class)
        fun saveFile(name: String, deltaConsumer: GitDeltaConsumer, modify: Boolean) {
            val gitDeltaConsumer: GitDeltaConsumer = deltaConsumer
            val current: GitTreeUpdate = treeStack.element()
            val entry: GitTreeEntry? = current.entries[name]
            val originalId: GitObject<ObjectId>? = gitDeltaConsumer.originalId
            if (modify xor (entry != null)) {
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_UP_TO_DATE, "Working copy is not up-to-date: " + getFullPath(name)))
            }
            val objectId: GitObject<ObjectId>? = gitDeltaConsumer.getObjectId()
            if (objectId == null) {
                // Content not updated.
                if (originalId == null) {
                    throw SVNException(SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, "Added file without content: " + getFullPath(name)))
                }
                return
            }
            current.entries[name] = GitTreeEntry(getFileMode(gitDeltaConsumer.properties), objectId, name)
            commitActions.add(VcsConsumer { action: CommitAction -> action.checkProperties(name, gitDeltaConsumer.properties, gitDeltaConsumer) })
        }

        private fun getFileMode(props: Map<String, String>): FileMode {
            if (props.containsKey(SVNProperty.SPECIAL)) return FileMode.SYMLINK
            if (props.containsKey(SVNProperty.EXECUTABLE)) return FileMode.EXECUTABLE_FILE
            return FileMode.REGULAR_FILE
        }

        @Throws(SVNException::class)
        fun delete(name: String) {
            val current = treeStack.element()
            current.entries.remove(name) ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, getFullPath(name)))
        }

        @Throws(SVNException::class, IOException::class)
        fun commit(userInfo: User, message: String): GitRevision? {
            val root: GitTreeUpdate = treeStack.element()
            val treeId: ObjectId = root.buildTree(inserter)
            log.debug("Create tree {} for commit.", treeId.name())
            val commitBuilder = CommitBuilder()
            val ident: PersonIdent = createIdent(userInfo)
            commitBuilder.author = ident
            commitBuilder.committer = ident
            commitBuilder.message = message
            val parentCommit: RevCommit? = revision.gitNewCommit
            if (parentCommit != null) {
                commitBuilder.setParentId(parentCommit.id)
            }
            commitBuilder.setTreeId(treeId)
            val commitId: ObjectId = inserter.insert(commitBuilder)
            inserter.flush()
            log.info("Create commit {}: {}", commitId.name(), StringHelper.getFirstLine(message))
            if (filterMigration(RevWalk(branch.repository.git).parseTree(treeId)) != 0) {
                log.info("Need recreate tree after filter migration.")
                return null
            }
            synchronized(pushLock) {
                log.info("Validate properties")
                validateProperties(RevWalk(branch.repository.git).parseTree(treeId))
                log.info("Try to push commit in branch: {}", branch)
                if (!pusher.push(branch.repository.git, commitId, branch.gitBranch, userInfo)) {
                    log.info("Non fast forward push rejected")
                    return null
                }
                log.info("Commit is pushed")
                branch.updateRevisions()
                return branch.getRevision(commitId)
            }
        }

        private fun createIdent(userInfo: User): PersonIdent {
            return PersonIdent(userInfo.realName, userInfo.email ?: "")
        }

        @Throws(IOException::class, SVNException::class)
        private fun filterMigration(tree: RevTree): Int {
            val root: GitFile = GitFileTreeEntry.create(branch, tree, 0)
            val validator = GitFilterMigration(root)
            for (validateAction: VcsConsumer<CommitAction> in commitActions) {
                validateAction.accept(validator)
            }
            return validator.done()
        }

        @Throws(IOException::class, SVNException::class)
        private fun validateProperties(tree: RevTree) {
            val root: GitFile = GitFileTreeEntry.create(branch, tree, 0)
            val validator = GitPropertyValidator(root)
            for (validateAction: VcsConsumer<CommitAction> in commitActions) {
                validateAction.accept(validator)
            }
            validator.done()
        }

        @Throws(SVNException::class, IOException::class)
        fun checkUpToDate(path: String, rev: Int) {
            val file: GitFile? = revision.getFile(path)
            if (file == null) {
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, path))
            } else if (file.lastChange.id > rev) {
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_UP_TO_DATE, "Working copy is not up-to-date: $path"))
            }
        }

        @Throws(SVNException::class, IOException::class)
        fun checkLock(path: String) {
            val iter: Iterator<LockDesc> = lockManager.getLocks(user, branch, path, Depth.Infinity)
            while (iter.hasNext()) checkLockDesc(iter.next())
        }

        @Throws(SVNException::class)
        private fun checkLockDesc(lockDesc: LockDesc?) {
            if (lockDesc != null) {
                val token: String? = locks[lockDesc.path]
                if (lockDesc.token != token) throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_BAD_LOCK_TOKEN, String.format("Cannot verify lock on path '%s'; no matching lock-token available", lockDesc.path)))
            }
        }

        init {
            treeStack = ArrayDeque()
            treeStack.push(GitTreeUpdate("", originalTree))
        }
    }

    companion object {
        const val keepFileName: String = ".keep"
        val keepFileContents: ByteArray = GitRepository.emptyBytes
        private const val MAX_PROPERTY_ERRORS: Int = 50
        private val log: Logger = Loggers.git
    }
}
