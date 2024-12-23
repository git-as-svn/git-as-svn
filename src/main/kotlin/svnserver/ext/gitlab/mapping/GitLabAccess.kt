/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping

import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.GitLabApiException
import org.gitlab4j.api.models.AccessLevel
import org.gitlab4j.api.models.Owner
import org.gitlab4j.api.models.Project
import org.mapdb.HTreeMap
import org.mapdb.Serializer
import ru.bozaro.gitlfs.common.JsonHelper
import svnserver.SerializableOptional
import svnserver.auth.User
import svnserver.context.LocalContext
import svnserver.ext.gitlab.auth.GitLabUserDB
import svnserver.ext.gitlab.config.GitLabContext
import svnserver.repository.VcsAccess
import java.io.FileNotFoundException
import java.io.IOException
import java.io.Serializable
import java.net.HttpURLConnection
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit

private class GitlabUserCache(user: Owner) : Serializable {
    val id: Long? = user.id
    val name: String? = user.name
}

private class GitlabProjectCache(project: Project) : Serializable {
    val projectAccess: AccessLevel? = project.permissions?.projectAccess?.accessLevel
    val projectGroupAccess: AccessLevel? = project.permissions?.groupAccess?.accessLevel
    val owner: GitlabUserCache? = if (project.owner == null) null else GitlabUserCache(project.owner)
}

/**
 * Access control by GitLab server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
internal class GitLabAccess(local: LocalContext, config: GitLabMappingConfig, private val gitlabProject: Project, private val relativeRepoPath: Path, private val gitlabContext: GitLabContext) : VcsAccess {
    private val cache = local.shared.cacheDB.hashMap("gitlab.projectAccess.${gitlabProject.id}", Serializer.STRING, Serializer.JAVA)
        .expireAfterCreate(config.cacheTimeSec, TimeUnit.SECONDS)
        .expireAfterUpdate(config.cacheTimeSec, TimeUnit.SECONDS)
        .expireAfterGet(config.cacheTimeSec, TimeUnit.SECONDS)
        .expireMaxSize(config.cacheMaximumSize)
        .createOrOpen() as HTreeMap<String, SerializableOptional<GitlabProjectCache>>

    private val anonymousApi = GitLabApi(gitlabContext.gitLabUrl, null)

    @Throws(IOException::class)
    override fun canRead(user: User, branch: String, path: String): Boolean {
        return getProjectViaSudo(user) != null
    }

    @Throws(IOException::class)
    override fun canWrite(user: User, branch: String, path: String): Boolean {
        if (user.isAnonymous) return false
        val project = getProjectViaSudo(user) ?: return false
        if (isProjectOwner(project, user)) return true
        return hasAccess(project.projectAccess, AccessLevel.DEVELOPER)
                || hasAccess(project.projectGroupAccess, AccessLevel.DEVELOPER)
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

        val userDetails = HashMap<String, Any>()
        if (userId != null)
            userDetails["userid"] = userId
        userDetails["username"] = user.username
        userDetails["protocol"] = glProtocol

        val hooksPayload = HashMap<String, Any>()
        hooksPayload["binary_directory"] = gitlabContext.config.gitalyBinDir
        hooksPayload["internal_socket"] = gitlabContext.config.gitalySocket
        hooksPayload["internal_socket_token"] = gitlabContext.config.gitalyToken
        hooksPayload["repository"] = gitalyRepoString

        // TODO: need to get this from GitLab API
        // hooksPayload["object_format"] = gitlabProject.repositoryObjectFormat
        hooksPayload["object_format"] = "sha1"

        hooksPayload["receive_hooks_payload"] = userDetails
        hooksPayload["user_details"] = userDetails

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

    private fun isProjectOwner(project: GitlabProjectCache, user: User): Boolean {
        if (user.isAnonymous) {
            return false
        }
        val owner = project.owner ?: return false
        return owner.id.toString() == user.externalId || owner.name == user.username
    }

    private fun hasAccess(access: AccessLevel?, level: AccessLevel): Boolean {
        return access != null && access >= level
    }

    @Throws(IOException::class)
    private fun getProjectViaSudo(user: User): GitlabProjectCache? {
        val key = if (user.isAnonymous) {
            ""
        } else {
            val id = user.externalId ?: user.username
            check(id.isNotEmpty()) { "Found user without identificator: $user" }
            id
        }

        return cache.computeIfAbsent(key) { userId ->
            try {
                val result = if (userId.isEmpty()) {
                    anonymousApi.projectApi.getProject(gitlabProject.id)
                } else {
                    gitlabContext.api.duplicate().use {
                        it.setSudoAsId(userId.toLong())
                        it.projectApi.getProject(gitlabProject.id)
                    }
                }
                SerializableOptional(GitlabProjectCache(result))
            } catch (e: GitLabApiException) {
                if (e.httpStatus == HttpURLConnection.HTTP_NOT_FOUND) {
                    SerializableOptional(null)
                } else {
                    throw e
                }
            }
        }.value
    }
}
