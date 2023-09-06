/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping

import org.apache.commons.collections4.trie.PatriciaTrie
import org.gitlab.api.GitlabAPI
import org.gitlab.api.models.GitlabAccessLevel
import org.gitlab.api.models.GitlabProject
import org.gitlab.api.models.GitlabUser
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
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit

private class GitlabUserCache(user: GitlabUser) : Serializable {
    val id: Int? = user.id
    val name: String? = user.name
}

private class GitlabProjectCache(project: GitlabProject) : Serializable {
    val projectAccess: GitlabAccessLevel? = project.permissions?.projectAccess?.accessLevel
    val projectGroupAccess: GitlabAccessLevel? = project.permissions?.projectGroupAccess?.accessLevel
    val owner: GitlabUserCache? = if (project.owner == null) null else GitlabUserCache(project.owner)
}

/**
 * Access control by GitLab server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
internal class GitLabAccess(local: LocalContext, config: GitLabMappingConfig, private val gitlabProject: GitlabProject, private val relativeRepoPath: Path, private val gitlabContext: GitLabContext) : VcsAccess {
    private val cache = local.shared.cacheDB.hashMap("gitlab.projectAccess.${gitlabProject.id}", Serializer.STRING, Serializer.JAVA)
        .expireAfterCreate(config.cacheTimeSec, TimeUnit.SECONDS)
        .expireAfterUpdate(config.cacheTimeSec, TimeUnit.SECONDS)
        .expireMaxSize(config.cacheMaximumSize)
        .createOrOpen() as HTreeMap<String, SerializableOptional<GitlabProjectCache>>

    @Throws(IOException::class)
    override fun canRead(user: User, branch: String, path: String): Boolean {
        return getProjectViaSudo(user) != null
    }

    @Throws(IOException::class)
    override fun canWrite(user: User, branch: String, path: String): Boolean {
        if (user.isAnonymous) return false
        val project = getProjectViaSudo(user) ?: return false
        if (isProjectOwner(project, user)) return true
        return hasAccess(project.projectAccess, GitlabAccessLevel.Developer)
                || hasAccess(project.projectGroupAccess, GitlabAccessLevel.Developer)
    }

    @Throws(IOException::class)
    override fun updateEnvironment(environment: MutableMap<String, String>, user: User) {
        val glRepository = String.format("project-%s", gitlabProject.id)
        val glProtocol = gitlabContext.config.glProtocol.name.lowercase()
        val userId: String? = if (user.externalId == null) null else GitLabUserDB.PREFIX_USER + user.externalId

        val gitalyRepo = PatriciaTrie<Any>()
        gitalyRepo["storageName"] = "default"
        gitalyRepo["glRepository"] = glRepository
        gitalyRepo["relativePath"] = relativeRepoPath.toString()
        gitalyRepo["glProjectPath"] = gitlabProject.pathWithNamespace
        val gitalyRepoString = JsonHelper.mapper.writeValueAsString(gitalyRepo)

        val userDetails = PatriciaTrie<Any>()
        if (userId != null)
            userDetails["userid"] = userId
        userDetails["username"] = user.username
        userDetails["protocol"] = glProtocol

        val hooksPayload = PatriciaTrie<Any>()
        hooksPayload["binary_directory"] = gitlabContext.config.gitalyBinDir
        hooksPayload["internal_socket"] = gitlabContext.config.gitalySocket
        hooksPayload["internal_socket_token"] = gitlabContext.config.gitalyToken
        hooksPayload["repository"] = gitalyRepoString
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

    private fun hasAccess(access: GitlabAccessLevel?, level: GitlabAccessLevel): Boolean {
        return access != null && access.accessValue >= level.accessValue
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
                    GitlabAPI.connect(gitlabContext.gitLabUrl, null).getProject(gitlabProject.id)
                } else {
                    val api = gitlabContext.connect()
                    val tailUrl = GitlabProject.URL + "/" + gitlabProject.id + "?sudo=" + userId
                    api.retrieve().to(tailUrl, GitlabProject::class.java)
                }
                SerializableOptional(GitlabProjectCache(result))
            } catch (e: FileNotFoundException) {
                SerializableOptional(null)
            }
        }.value
    }
}
