/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.slf4j.Logger
import svnserver.Loggers
import svnserver.config.ConfigHelper
import svnserver.context.Shared
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Git submodules list.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitSubmodules : Shared {
    private val repositories: MutableSet<Repository> = CopyOnWriteArraySet()

    internal constructor()
    constructor(basePath: Path, paths: Collection<String>) {
        for (path: String in paths) {
            val file: Path = ConfigHelper.joinPath(basePath, path)
            if (!Files.exists(file)) throw FileNotFoundException(file.toString())
            log.info("Linked repository path: {}", file)
            repositories.add(FileRepository(file.toFile()))
        }
    }

    @Throws(IOException::class)
    fun findCommit(objectId: ObjectId): GitObject<RevCommit>? {
        for (repo: Repository in repositories) if (repo.objectDatabase.has(objectId)) return GitObject(repo, RevWalk(repo).parseCommit(objectId))
        return null
    }

    fun register(repository: Repository) {
        repositories.add(repository)
    }

    fun unregister(repository: Repository) {
        repositories.remove(repository)
    }

    companion object {
        private val log: Logger = Loggers.git
    }
}
