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
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Helper for creating svn layout in git repository.
 *
 * @author a.navrotskiy
 */
object LayoutHelper {
    private const val OLD_CACHE_REF: String = "refs/git-as-svn/v0"
    private const val PREFIX_REF: String = "refs/git-as-svn/v1/"
    private const val ENTRY_COMMIT_REF: String = "commit.ref"
    private const val ENTRY_ROOT: String = "svn"
    private const val ENTRY_UUID: String = "uuid"

    @Throws(IOException::class)
    fun initRepository(repository: Repository, branch: String): Ref {
        var ref: Ref? = repository.exactRef(PREFIX_REF + branch)
        if (ref == null) {
            val old: Ref? = repository.exactRef(OLD_CACHE_REF)
            if (old != null) {
                val refUpdate: RefUpdate = repository.updateRef(PREFIX_REF + branch)
                refUpdate.setNewObjectId(old.objectId)
                refUpdate.update()
            }
        }
        if (ref == null) {
            val revision: ObjectId = createFirstRevision(repository)
            val refUpdate: RefUpdate = repository.updateRef(PREFIX_REF + branch)
            refUpdate.setNewObjectId(revision)
            refUpdate.update()
            ref = repository.exactRef(PREFIX_REF + branch)
            if (ref == null) {
                throw IOException("Can't initialize repository.")
            }
        }
        val old: Ref? = repository.exactRef(OLD_CACHE_REF)
        if (old != null) {
            val refUpdate: RefUpdate = repository.updateRef(OLD_CACHE_REF)
            refUpdate.isForceUpdate = true
            refUpdate.delete()
        }
        return ref
    }

    @Throws(IOException::class)
    private fun createFirstRevision(repository: Repository): ObjectId {
        // Generate UUID.
        repository.newObjectInserter().use { inserter ->
            val uuidId: ObjectId = inserter.insert(Constants.OBJ_BLOB, UUID.randomUUID().toString().toByteArray(StandardCharsets.UTF_8))
            // Create svn empty tree.
            val treeId: ObjectId = inserter.insert(TreeFormatter())
            // Create commit tree.
            val rootBuilder = TreeFormatter()
            rootBuilder.append(ENTRY_ROOT, FileMode.TREE, treeId)
            rootBuilder.append(ENTRY_UUID, FileMode.REGULAR_FILE, uuidId)
            ObjectChecker().checkTree(rootBuilder.toByteArray())
            val rootId: ObjectId = inserter.insert(rootBuilder)
            // Create first commit with message.
            val commitBuilder = CommitBuilder()
            commitBuilder.author = PersonIdent("", "", 0, 0)
            commitBuilder.committer = PersonIdent("", "", 0, 0)
            commitBuilder.message = "#0: Initial revision"
            commitBuilder.setTreeId(rootId)
            val commitId: ObjectId = inserter.insert(commitBuilder)
            inserter.flush()
            return commitId
        }
    }

    @Throws(IOException::class)
    fun createCacheCommit(inserter: ObjectInserter, parent: ObjectId, commit: RevCommit, revisionId: Int, revBranches: Map<String, ObjectId>): ObjectId {
        val treeBuilder = TreeFormatter()
        treeBuilder.append(ENTRY_COMMIT_REF, commit)
        treeBuilder.append("svn", FileMode.TREE, createSvnLayoutTree(inserter, revBranches))
        ObjectChecker().checkTree(treeBuilder.toByteArray())
        val rootTree: ObjectId = inserter.insert(treeBuilder)
        val commitBuilder = CommitBuilder()
        commitBuilder.author = commit.authorIdent
        commitBuilder.committer = commit.committerIdent
        commitBuilder.message = "#" + revisionId + ": " + commit.fullMessage
        commitBuilder.addParentId(parent)
        // Add reference to original commit as parent for prevent commit removing by `git gc` (see #118).
        commitBuilder.addParentId(commit)
        commitBuilder.setTreeId(rootTree)
        return inserter.insert(commitBuilder)
    }

    @Throws(IOException::class)
    private fun createSvnLayoutTree(inserter: ObjectInserter, revBranches: Map<String, ObjectId>): ObjectId? {
        val stack: Deque<TreeFormatter> = ArrayDeque()
        stack.add(TreeFormatter())
        var dir = ""
        val checker = ObjectChecker()
        for (entry: Map.Entry<String, ObjectId> in TreeMap(revBranches).entries) {
            val path: String = entry.key
            // Save already added nodes.
            while (!path.startsWith(dir)) {
                val index: Int = dir.lastIndexOf('/', dir.length - 2) + 1
                val tree: TreeFormatter = stack.pop()
                checker.checkTree(tree.toByteArray())
                stack.element().append(dir.substring(index, dir.length - 1), FileMode.TREE, inserter.insert(tree))
                dir = dir.substring(0, index)
            }
            // Go deeper.
            var index: Int = path.indexOf('/', dir.length) + 1
            while (index < path.length) {
                dir = path.substring(0, index)
                stack.push(TreeFormatter())
                index = path.indexOf('/', index) + 1
            }
            // Add commit to tree.
            run {
                val index: Int = path.lastIndexOf('/', path.length - 2) + 1
                stack.element().append(path.substring(index, path.length - 1), FileMode.GITLINK, entry.value)
            }
        }
        // Save already added nodes.
        while (!dir.isEmpty()) {
            val index: Int = dir.lastIndexOf('/', dir.length - 2) + 1
            val tree: TreeFormatter = stack.pop()
            checker.checkTree(tree.toByteArray())
            stack.element().append(dir.substring(index, dir.length - 1), FileMode.TREE, inserter.insert(tree))
            dir = dir.substring(0, index)
        }
        // Save root tree to disk.
        val rootTree: TreeFormatter = stack.pop()
        checker.checkTree(rootTree.toByteArray())
        if (!stack.isEmpty()) {
            throw IllegalStateException()
        }
        return inserter.insert(rootTree)
    }

    @Throws(IOException::class)
    fun loadOriginalCommit(reader: ObjectReader, cacheCommit: ObjectId?): RevCommit? {
        val revWalk = RevWalk(reader)
        if (cacheCommit != null) {
            val revCommit: RevCommit = revWalk.parseCommit(cacheCommit)
            revWalk.parseTree(revCommit.tree)
            val treeParser = CanonicalTreeParser(GitRepository.emptyBytes, reader, revCommit.tree)
            while (!treeParser.eof()) {
                if ((treeParser.entryPathString == ENTRY_COMMIT_REF)) {
                    return revWalk.parseCommit(treeParser.entryObjectId)
                }
                treeParser.next()
            }
        }
        return null
    }

    @Throws(IOException::class)
    fun loadRepositoryId(objectReader: ObjectReader, commit: ObjectId?): String {
        val revWalk = RevWalk(objectReader)
        val treeWalk: TreeWalk? = TreeWalk.forPath(objectReader, ENTRY_UUID, revWalk.parseCommit(commit).tree)
        if (treeWalk != null) {
            return GitRepository.loadContent(objectReader, treeWalk.getObjectId(0))
        }
        throw FileNotFoundException(ENTRY_UUID)
    }
}
