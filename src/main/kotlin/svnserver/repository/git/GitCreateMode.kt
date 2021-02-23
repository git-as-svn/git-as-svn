/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import org.apache.commons.io.IOUtils
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.*
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Git create repository behaviour.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
enum class GitCreateMode {
    ERROR {
        @Throws(IOException::class)
        override fun createRepository(path: Path, branches: Set<String>): Repository {
            throw FileNotFoundException(path.toString())
        }
    },
    EMPTY {
        @Throws(IOException::class)
        override fun createRepository(path: Path, branches: Set<String>): Repository {
            return createRepository(path)
        }
    },
    EXAMPLE {
        @Throws(IOException::class)
        override fun createRepository(path: Path, branches: Set<String>): Repository {
            val repository: Repository = createRepository(path)
            val revision: ObjectId = createFirstRevision(repository)
            for (branch: String in branches) {
                val refUpdate: RefUpdate = repository.updateRef(Constants.R_HEADS + branch)
                refUpdate.setNewObjectId(revision)
                refUpdate.update()
            }
            return repository
        }
    };

    @Throws(IOException::class)
    abstract fun createRepository(path: Path, branches: Set<String>): Repository

    companion object {
        @Throws(IOException::class)
        protected fun createRepository(path: Path): Repository {
            val repository = FileRepository(Files.createDirectories(path).toFile())
            repository.create(true)
            return repository
        }

        @Throws(IOException::class)
        private fun createFirstRevision(repository: Repository): ObjectId {
            repository.newObjectInserter().use { inserter ->
                // Create commit tree.
                val rootBuilder = TreeFormatter()
                rootBuilder.append(".gitattributes", FileMode.REGULAR_FILE, insertFile(inserter, "example/_gitattributes"))
                ObjectChecker().checkTree(rootBuilder.toByteArray())
                val rootId: ObjectId = inserter.insert(rootBuilder)
                // Create first commit with message.
                val commitBuilder = CommitBuilder()
                commitBuilder.author = PersonIdent("", "", 0, 0)
                commitBuilder.committer = PersonIdent("", "", 0, 0)
                commitBuilder.message = "Initial commit"
                commitBuilder.setTreeId(rootId)
                val commitId: ObjectId = inserter.insert(commitBuilder)
                inserter.flush()
                return commitId
            }
        }

        @Throws(IOException::class)
        private fun insertFile(inserter: ObjectInserter, resourceName: String): AnyObjectId {
            GitCreateMode::class.java.getResourceAsStream(resourceName).use { stream ->
                if (stream == null) {
                    throw FileNotFoundException(resourceName)
                }
                return inserter.insert(Constants.OBJ_BLOB, IOUtils.toByteArray(stream))
            }
        }
    }
}
