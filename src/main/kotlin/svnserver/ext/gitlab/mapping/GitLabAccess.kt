/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import org.gitlab.api.GitlabAPI
import org.gitlab.api.models.GitlabAccessLevel
import org.gitlab.api.models.GitlabProject
import org.gitlab.api.models.GitlabProjectAccessLevel
import ru.bozaro.gitlfs.common.JsonHelper
import svnserver.auth.User
import svnserver.context.LocalContext
import svnserver.ext.gitlab.auth.GitLabUserDB
import svnserver.ext.gitlab.config.GitLabContext
import svnserver.repository.VcsAccess
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * Access control by GitLab server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
internal class GitLabAccess(local: LocalContext, config: GitLabMappingConfig, private val gitlabProject: GitlabProject, private val relativeRepoPath: Path, private val gitlabContext: GitLabContext) : VcsAccess {
    private val cache: LoadingCache<String, GitlabProject>

    @Throws(IOException::class)
    override fun canRead(user: User, branch: String, path: String): Boolean {
        return try {
            getProjectViaSudo(user)
            true
        } catch (ignored: FileNotFoundException) {
            false
        }
    }

    @Throws(IOException::class)
    override fun canWrite(user: User, branch: String, path: String): Boolean {
        return if (user.isAnonymous) false else try {
            val project = getProjectViaSudo(user)
            if (isProjectOwner(project, user)) return true
            val permissions = project.permissions ?: return false
            (hasAccess(permissions.projectAccess, GitlabAccessLevel.Developer)
                    || hasAccess(permissions.projectGroupAccess, GitlabAccessLevel.Developer))
        } catch (ignored: FileNotFoundException) {
            false
        }
    }

    @Throws(IOException::class)
    override fun updateEnvironment(environment: MutableMap<String, String>, user: User) {
        val glRepository = String.format("project-%s", gitlabProject.id)
        val glProtocol = gitlabContext.config.glProtocol.name.lowercase()
        val userId: String? = if (user.externalId == null) null else GitLabUserDB.PREFIX_USER + user.externalId

        val gitalyRepo = HashMap<String, Any>()
        gitalyRepo["storageName"] = "default"
        gitalyRepo["glRepository"] = glRepository
        gitalyRepo["relativePath"] = relativeRepoPath.toString()
        gitalyRepo["glProjectPath"] = gitlabProject.pathWithNamespace
        val gitalyRepoString = JsonHelper.mapper.writeValueAsString(gitalyRepo)

        val receiveHooksPayload = HashMap<String, Any>()
        if (userId != null)
            receiveHooksPayload["userid"] = userId
        receiveHooksPayload["username"] = user.username
        receiveHooksPayload["protocol"] = glProtocol

        val hooksPayload = HashMap<String, Any>()
        hooksPayload["binary_directory"] = gitlabContext.config.gitalyBinDir
        hooksPayload["internal_socket"] = gitlabContext.config.gitalySocket
        hooksPayload["internal_socket_token"] = gitlabContext.config.gitalyToken
        hooksPayload["receive_hooks_payload"] = receiveHooksPayload
        hooksPayload["repository"] = gitalyRepoString

        /*
          These are required for GitLab hooks
          See:
          https://github.com/git-as-svn/git-as-svn/issues/271
          https://github.com/git-as-svn/git-as-svn/issues/337
          https://github.com/git-as-svn/git-as-svn/issues/347
          https://github.com/git-as-svn/git-as-svn/issues/355
          https://github.com/git-as-svn/git-as-svn/issues/367
        */
        environment["GITALY_BIN_DIR"] = gitlabContext.config.gitalyBinDir
        environment["GITALY_HOOKS_PAYLOAD"] = Base64.getEncoder().encodeToString(JsonHelper.mapper.writeValueAsBytes(hooksPayload))
        environment["GITALY_REPO"] = gitalyRepoString
        environment["GITALY_SOCKET"] = gitlabContext.config.gitalySocket
        environment["GITALY_TOKEN"] = gitlabContext.config.gitalyToken
        if (userId != null)
            environment["GL_ID"] = userId
        environment["GL_USERNAME"] = user.username
        environment["GL_PROTOCOL"] = glProtocol
        environment["GL_REPOSITORY"] = glRepository
    }

    private fun isProjectOwner(project: GitlabProject, user: User): Boolean {
        if (user.isAnonymous) {
            return false
        }
        val owner = project.owner ?: return false
        return owner.id.toString() == user.externalId || owner.name == user.username
    }

    private fun hasAccess(access: GitlabProjectAccessLevel?, level: GitlabAccessLevel): Boolean {
        if (access == null) return false
        val accessLevel = access.accessLevel
        return accessLevel != null && accessLevel.accessValue >= level.accessValue
    }

    @Throws(IOException::class)
    private fun getProjectViaSudo(user: User): GitlabProject {
        return try {
            if (user.isAnonymous) return cache[""]
            val key = user.externalId ?: user.username
            check(key.isNotEmpty()) { "Found user without identificator: $user" }
            cache[key]
        } catch (e: ExecutionException) {
            if (e.cause is IOException) {
                throw (e.cause as IOException?)!!
            }
            throw IllegalStateException(e)
        }
    }

    init {
        val context: GitLabContext = GitLabContext.sure(local.shared)
        cache = CacheBuilder.newBuilder()
            .maximumSize(config.cacheMaximumSize.toLong())
            .expireAfterWrite(config.cacheTimeSec.toLong(), TimeUnit.SECONDS)
            .build(object : CacheLoader<String, GitlabProject>() {
                @Throws(Exception::class)
                override fun load(userId: String): GitlabProject {
                    if (userId.isEmpty()) return GitlabAPI.connect(context.gitLabUrl, null).getProject(gitlabProject.id)
                    val api = context.connect()
                    val tailUrl = GitlabProject.URL + "/" + gitlabProject.id + "?sudo=" + userId
                    return api.retrieve().to(tailUrl, GitlabProject::class.java)
                }
            })
    }
}
