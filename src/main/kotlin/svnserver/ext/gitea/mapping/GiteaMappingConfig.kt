/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.mapping

import io.gitea.ApiException
import io.gitea.api.UserApi
import org.tmatesoft.svn.core.SVNException
import svnserver.config.GitRepositoryConfig
import svnserver.config.RepositoryMappingConfig
import svnserver.context.SharedContext
import svnserver.ext.gitea.config.GiteaContext
import svnserver.repository.RepositoryMapping
import svnserver.repository.git.GitCreateMode
import java.io.IOException
import java.nio.file.Path
import java.util.function.Consumer

/**
 * Repository list mapping.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
class GiteaMappingConfig private constructor(var path: String, var createMode: GitCreateMode) : RepositoryMappingConfig {
    var template: GitRepositoryConfig = GitRepositoryConfig(createMode)
    private var watcher: DirectoryWatcher? = null
    var cacheTimeSec = 15
    var cacheMaximumSize = 1000

    constructor() : this("/var/git/repositories/", GitCreateMode.ERROR)
    constructor(path: Path, createMode: GitCreateMode) : this(path.toAbsolutePath().toString(), createMode)

    @Throws(IOException::class)
    override fun create(context: SharedContext, canUseParallelIndexing: Boolean): RepositoryMapping<*> {
        val giteaContext = context.sure(GiteaContext::class.java)
        val apiClient = giteaContext.connect()
        val userApi = UserApi(apiClient)
        // Get repositories.
        val mapping = GiteaMapping(context, this)
        try {
            val usersList = userApi.userSearch(null, null, null)
            for (u in usersList.data) {
                val repositories = userApi.userListRepos(u.login)
                for (repository in repositories) {
                    mapping.addRepository(repository)
                }
            }
        } catch (e: ApiException) {
            throw RuntimeException("Failed to initialize", e)
        }

        // Add directory watcher
        if (watcher == null || !watcher!!.isAlive) {
            watcher = DirectoryWatcher(path, GiteaMapper(apiClient, mapping))
        }
        val init = Consumer { repository: GiteaProject ->
            try {
                repository.initRevisions()
            } catch (e: IOException) {
                throw RuntimeException(String.format("[%s]: failed to initialize", repository.context.name), e)
            } catch (e: SVNException) {
                throw RuntimeException(String.format("[%s]: failed to initialize", repository.context.name), e)
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
