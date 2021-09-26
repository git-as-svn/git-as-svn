/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping

import com.google.common.hash.Hashing
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jgit.util.StringUtils
import org.gitlab.api.GitlabAPI
import org.gitlab.api.GitlabAPIException
import org.gitlab.api.models.GitlabProject
import org.tmatesoft.svn.core.SVNException
import svnserver.Loggers
import svnserver.StringHelper
import svnserver.config.ConfigHelper
import svnserver.context.LocalContext
import svnserver.context.SharedContext
import svnserver.ext.gitlab.config.GitLabContext
import svnserver.ext.web.server.WebServer
import svnserver.repository.RepositoryMapping
import svnserver.repository.VcsAccess
import svnserver.repository.git.GitBranch
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentSkipListMap
import java.util.stream.Collectors

/**
 * Simple repository mapping by predefined list.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal class GitLabMapping(private val context: SharedContext, private val config: GitLabMappingConfig, private val gitLabContext: GitLabContext) : RepositoryMapping<GitLabProject> {
    override val mapping: NavigableMap<String, GitLabProject> = ConcurrentSkipListMap()

    @Throws(IOException::class)
    fun updateRepository(project: GitlabProject): GitLabProject? {
        val branches = getBranchesToExpose(project)
        if (branches.isEmpty()) {
            removeRepository(project.id, project.pathWithNamespace)
            return null
        }
        val projectKey = StringHelper.normalizeDir(project.pathWithNamespace)
        val oldProject = mapping[projectKey]
        if (oldProject != null && oldProject.projectId == project.id) {
            val oldBranches = oldProject.branches.values.stream().map { obj: GitBranch -> obj.shortBranchName }.collect(Collectors.toSet())
            if (oldBranches == branches) // Old project is good enough already
                return oldProject
        }

        // TODO: do not drop entire repo here, instead only apply diff - add missing branches and remove unneeded
        removeRepository(project.id, project.pathWithNamespace)
        val basePath = ConfigHelper.joinPath(context.basePath, config.path)
        val sha256 = Hashing.sha256().hashString(project.id.toString(), Charset.defaultCharset()).toString()
        var relativeRepoPath = Paths.get(HASHED_PATH, sha256.substring(0, 2), sha256.substring(2, 4), "$sha256.git")
        var repoPath = basePath.resolve(relativeRepoPath)
        if (!Files.exists(repoPath)) {
            relativeRepoPath = Paths.get(project.pathWithNamespace + ".git")
            repoPath = basePath.resolve(relativeRepoPath)
        }
        val local = LocalContext(context, project.pathWithNamespace)
        local.add(VcsAccess::class.java, GitLabAccess(local, config, project, relativeRepoPath, gitLabContext))
        val repository = config.template.create(local, repoPath, branches)
        val newProject = GitLabProject(local, repository, project.id)
        return if (mapping.compute(projectKey) { _: String?, value: GitLabProject? -> if (value != null && value.projectId == project.id) value else newProject } == newProject) {
            newProject
        } else null
    }

    private fun removeRepository(projectId: Int, projectName: String) {
        val projectKey = StringHelper.normalizeDir(projectName)
        val project = mapping[projectKey]
        if (project != null && project.projectId == projectId) {
            if (mapping.remove(projectKey, project)) {
                project.close()
            }
        }
    }

    @Throws(IOException::class)
    override fun ready(context: SharedContext) {
        val api = gitLabContext.connect()

        // Web hook for repository list update.
        val webServer = context.sure(WebServer::class.java)
        val hookUrl = webServer.toUrl(gitLabContext.hookPath)
        val path = hookUrl.path
        webServer.addServlet(if (StringUtils.isEmptyOrNull(path)) "/" else path, GitLabHookServlet())
        try {
            if (!isHookInstalled(api, hookUrl.toString())) {
                api.addSystemHook(hookUrl.toString())
            }
        } catch (e: GitlabAPIException) {
            if (e.responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                log.warn("Unable to install gitlab hook {}: {}", hookUrl, e.message)
            } else {
                throw e
            }
        }
    }

    @Throws(IOException::class)
    private fun isHookInstalled(api: GitlabAPI, hookUrl: String): Boolean {
        val hooks = api.systemHooks
        for (hook in hooks) {
            if (hook.url == hookUrl) {
                return true
            }
        }
        return false
    }

    private inner class GitLabHookServlet : HttpServlet() {
        @Throws(ServletException::class, IOException::class)
        override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
            log.info("GitLab system hook fire ...")
            val event = parseEvent(req)
            val msg = "Can't parse event data"
            if (event?.eventName == null) {
                log.warn(msg)
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, msg)
                return
            }
            try {
                log.debug(event.eventName + " event happened, process ...")
                when (event.eventName) {
                    "project_create", "project_update", "project_rename", "project_transfer" -> {
                        if (event.projectId == null || event.pathWithNamespace == null) {
                            log.warn(msg)
                            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, msg)
                            return
                        }
                        val api = gitLabContext.connect()
                        val project = updateRepository(api.getProject(event.projectId))
                        if (project != null) {
                            log.info(event.eventName + " event happened, init project revisions ...")
                            project.initRevisions()
                        } else {
                            log.warn(event.eventName + " event happened, but can not found project!")
                        }
                        return
                    }
                    "project_destroy" -> {
                        if (event.projectId == null || event.pathWithNamespace == null) {
                            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Can't parse event data")
                            return
                        }
                        removeRepository(event.projectId, event.pathWithNamespace)
                    }
                    else -> {
                        // Ignore hook.
                        log.info(event.eventName + " event not process, ignore this hook event.")
                        return
                    }
                }
                super.doPost(req, resp)
            } catch (inored: FileNotFoundException) {
                log.warn("Event repository not exists: " + event.projectId)
            } catch (e: SVNException) {
                log.error("Event processing error: " + event.eventName, e)
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.message)
            }
        }

        private fun parseEvent(req: HttpServletRequest): GitLabHookEvent? {
            try {
                req.reader.use { reader -> return GitLabHookEvent.parseEvent(reader) }
            } catch (e: IOException) {
                log.warn("Can't read hook data", e)
                return null
            }
        }
    }

    companion object {
        private const val tagPrefix = "git-as-svn:"
        private val log = Loggers.gitlab
        private const val HASHED_PATH = "@hashed"
        private fun getBranchesToExpose(project: GitlabProject): Set<String> {
            val result = TreeSet<String>()
            for (tag in project.tagList) {
                if (!tag.startsWith(tagPrefix)) continue
                val branch = tag.substring(tagPrefix.length)
                if (branch.isEmpty()) continue
                result.add(branch)
            }
            return result
        }
    }
}
