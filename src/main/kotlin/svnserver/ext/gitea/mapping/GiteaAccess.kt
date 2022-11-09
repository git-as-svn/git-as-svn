/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.mapping

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import io.gitea.ApiException
import io.gitea.api.RepositoryApi
import io.gitea.model.Repository
import svnserver.auth.User
import svnserver.context.LocalContext
import svnserver.ext.gitea.config.GiteaContext
import svnserver.repository.VcsAccess
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * Access control by Gitea server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
internal class GiteaAccess(local: LocalContext, config: GiteaMappingConfig, private val repository: Repository) : VcsAccess {
    private val cache: LoadingCache<String, Repository>

    @Throws(IOException::class)
    override fun canRead(user: User, branch: String, path: String): Boolean {
        return try {
            val repository = getCachedProject(user)
            if (!repository.isPrivate) return true
            val permission = repository.permissions
            permission.isAdmin || permission.isPull
        } catch (ignored: FileNotFoundException) {
            false
        }
    }

    @Throws(IOException::class)
    override fun canWrite(user: User, branch: String, path: String): Boolean {
        return if (user.isAnonymous) false else try {
            val repository = getCachedProject(user)
            val permission = repository.permissions
            permission.isAdmin || permission.isPush
        } catch (ignored: FileNotFoundException) {
            false
        }
    }

    override fun updateEnvironment(environment: MutableMap<String, String>, user: User) {
        environment["GITEA_REPO_ID"] = "" + repository.id
        environment["GITEA_REPO_IS_WIKI"] = "false"
        environment["GITEA_REPO_NAME"] = repository.name
        environment["GITEA_REPO_USER_NAME"] = repository.owner.login
        environment["SSH_ORIGINAL_COMMAND"] = "git"
        if (user.username != null)
            environment["GITEA_PUSHER_NAME"] = if (user.realName.isNullOrEmpty()) user.username else user.realName
        if (user.email != null)
            environment["GITEA_PUSHER_EMAIL"] = user.email
        if (user.externalId != null)
            environment["GITEA_PUSHER_ID"] = user.externalId
    }

    @Throws(IOException::class)
    private fun getCachedProject(user: User): Repository {
        return try {
            if (user.isAnonymous) return cache[""]
            val key = user.username
            check(key.isNotEmpty()) { "Found user without identifier: $user" }
            cache[key]
        } catch (e: ExecutionException) {
            if (e.cause is IOException) {
                throw (e.cause as IOException?)!!
            }
            throw IllegalStateException(e)
        }
    }

    init {
        val projectId = repository.id
        val context: GiteaContext = GiteaContext.sure(local.shared)
        cache = CacheBuilder.newBuilder().maximumSize(config.cacheMaximumSize.toLong())
            .expireAfterWrite(config.cacheTimeSec.toLong(), TimeUnit.SECONDS).build(object : CacheLoader<String, Repository>() {
                @Throws(Exception::class)
                override fun load(username: String): Repository {
                    if (username.isEmpty()) {
                        try {
                            val apiClient = context.connect()
                            val repositoryApi = RepositoryApi(apiClient)
                            val repository = repositoryApi.repoGetByID(projectId)
                            if (!repository.isPrivate) {
                                // Munge the permissions
                                repository.permissions.isAdmin = false
                                repository.permissions.isPush = false
                                return repository
                            }
                            throw FileNotFoundException()
                        } catch (e: ApiException) {
                            if (e.code == 404) {
                                throw FileNotFoundException()
                            } else {
                                throw e
                            }
                        }
                    }
                    // Sudo as the user
                    return try {
                        val apiClient = context.connect(username)
                        val repositoryApi = RepositoryApi(apiClient)
                        repositoryApi.repoGetByID(projectId)
                    } catch (e: ApiException) {
                        if (e.code == 404) {
                            throw FileNotFoundException()
                        } else {
                            throw e
                        }
                    }
                }
            })
    }
}
