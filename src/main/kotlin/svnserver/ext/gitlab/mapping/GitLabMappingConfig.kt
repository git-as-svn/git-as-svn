/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping

import org.tmatesoft.svn.core.SVNException
import svnserver.config.GitRepositoryConfig
import svnserver.config.RepositoryMappingConfig
import svnserver.context.SharedContext
import svnserver.ext.gitlab.config.GitLabContext
import svnserver.repository.RepositoryMapping
import svnserver.repository.git.GitCreateMode
import java.io.IOException
import java.nio.file.Path
import java.util.function.Consumer

/**
 * Repository list mapping.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitLabMappingConfig private constructor(var path: String, var createMode: GitCreateMode) : RepositoryMappingConfig {
    var template: GitRepositoryConfig = GitRepositoryConfig(createMode)
    var cacheTimeSec = 15L
    var cacheMaximumSize = 1000L

    constructor() : this("/var/opt/gitlab/git-data/repositories/", GitCreateMode.ERROR)
    constructor(path: Path, createMode: GitCreateMode) : this(path.toAbsolutePath().toString(), createMode)

    @Throws(IOException::class)
    override fun create(context: SharedContext, canUseParallelIndexing: Boolean): RepositoryMapping<*> {
        val gitlab = context.sure(GitLabContext::class.java)
        val api = gitlab.connect()
        // Get repositories.
        val mapping = GitLabMapping(context, this, gitlab)
        for (project in api.projects) mapping.updateRepository(project)
        val init = Consumer { repository: GitLabProject ->
            try {
                repository.initRevisions()
            } catch (e: IOException) {
                throw RuntimeException(String.format("[%s]: failed to initialize", repository), e)
            } catch (e: SVNException) {
                throw RuntimeException(String.format("[%s]: failed to initialize", repository), e)
            }
        }
        if (canUseParallelIndexing) {
            mapping.mapping.values.parallelStream().forEach(init)
        } else {
            mapping.mapping.values.forEach(init)
        }
        return mapping
    }
}
