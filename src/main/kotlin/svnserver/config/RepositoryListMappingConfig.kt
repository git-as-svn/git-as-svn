/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config

import org.tmatesoft.svn.core.SVNException
import svnserver.StringHelper
import svnserver.auth.ACL
import svnserver.context.LocalContext
import svnserver.context.SharedContext
import svnserver.repository.RepositoryMapping
import svnserver.repository.VcsAccess
import svnserver.repository.git.GitBranch
import svnserver.repository.git.GitRepository
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.function.Consumer

/**
 * Repository list mapping.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class RepositoryListMappingConfig : RepositoryMappingConfig {
    private var repositories: Map<String, Entry> = TreeMap()
    private var groups: Map<String, Array<String>> = HashMap()

    @Throws(IOException::class)
    override fun create(context: SharedContext, canUseParallelIndexing: Boolean): RepositoryMapping<GitRepository> {
        val repos: NavigableMap<String, GitRepository> = TreeMap()
        val uniquePaths: MutableSet<Path> = HashSet()
        repositories.values.stream().map { entry: Entry -> entry.repository.path }.forEach { s: String ->
            if (!uniquePaths.add(Paths.get(s).toAbsolutePath())) throw IllegalStateException("Duplicate repositories in config: $s")
        }
        for (entry: Map.Entry<String, Entry> in repositories.entries) {
            val local = LocalContext(context, entry.key)
            local.add(VcsAccess::class.java, ACL(local.name, groups, entry.value.access))
            repos[StringHelper.normalizeDir(entry.key)] = entry.value.repository.create(local)
        }
        val init: Consumer<GitBranch> = Consumer { repository: GitBranch ->
            try {
                repository.updateRevisions()
            } catch (e: IOException) {
                throw RuntimeException(String.format("[%s]: failed to initialize", repository), e)
            } catch (e: SVNException) {
                throw RuntimeException(String.format("[%s]: failed to initialize", repository), e)
            }
        }
        if (canUseParallelIndexing) {
            repos
                .values
                .parallelStream()
                .flatMap { repo: GitRepository -> repo.branches.values.parallelStream() }
                .forEach(init)
        } else {
            repos
                .values
                .stream()
                .flatMap { repo: GitRepository -> repo.branches.values.stream() }
                .forEach(init)
        }
        return object : RepositoryMapping<GitRepository> {
            override val mapping: NavigableMap<String, GitRepository>
                get() = repos
        }
    }

    class Entry {
        /**
         * This should be Map<String></String>, Map<String></String>, AccessMode>> but snakeyaml doesn't support that. See https://bitbucket.org/asomov/snakeyaml/issues/361/list-does-not-create-property-objects
         */
        val access: Map<String, Map<String, String>> = HashMap()
        val repository: GitRepositoryConfig = GitRepositoryConfig()
    }
}
