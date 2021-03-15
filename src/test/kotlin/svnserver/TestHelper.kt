/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver

import org.apache.commons.io.FileUtils
import org.apache.commons.io.input.CharSequenceInputStream
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository
import org.eclipse.jgit.lib.Repository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Common test functions.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
object TestHelper {
    val logger: Logger = LoggerFactory.getLogger("test")
    fun saveFile(file: Path, content: String) {
        Files.write(file, content.toByteArray(StandardCharsets.UTF_8))
    }

    fun createTempDir(prefix: String): Path {
        return Files.createTempDirectory(findGitPath().parent.resolve("build/tmp"), "$prefix-").toAbsolutePath()
    }

    fun findGitPath(): Path {
        val root = Paths.get("").toAbsolutePath()
        var path = root
        while (true) {
            val repo = path.resolve(".git")
            if (Files.isDirectory(repo)) return repo
            path = path.parent
            checkNotNull(path) { "Repository not found from directory: $root" }
        }
    }

    fun deleteDirectory(file: Path) {
        FileUtils.deleteDirectory(file.toFile())
    }

    fun asStream(content: String): InputStream {
        return CharSequenceInputStream(content, StandardCharsets.UTF_8)
    }

    fun emptyRepository(): Repository {
        val repository: Repository = InMemoryRepository(DfsRepositoryDescription(null))
        repository.create()
        return repository
    }
}
