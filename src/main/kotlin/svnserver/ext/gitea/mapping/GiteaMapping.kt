/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.mapping

import io.gitea.model.Repository
import svnserver.StringHelper
import svnserver.config.ConfigHelper
import svnserver.context.LocalContext
import svnserver.context.SharedContext
import svnserver.repository.RepositoryMapping
import svnserver.repository.VcsAccess
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentSkipListMap

/**
 * Simple repository mapping by predefined list.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
internal class GiteaMapping(val context: SharedContext, private val config: GiteaMappingConfig) : RepositoryMapping<GiteaProject> {
    override val mapping: NavigableMap<String, GiteaProject> = ConcurrentSkipListMap()

    @Throws(IOException::class)
    fun addRepository(repository: Repository): GiteaProject? {
        val projectName = repository.fullName
        val projectKey = StringHelper.normalizeDir(projectName)
        val oldProject = mapping[projectKey]
        if (oldProject == null || oldProject.projectId != repository.id) {
            val basePath = ConfigHelper.joinPath(context.basePath, config.path)
            // the repository name is lowercased as per gitea cmd/serv.go:141
            val repoPath = ConfigHelper.joinPath(basePath, repository.fullName.lowercase() + ".git")
            val local = LocalContext(context, repository.fullName)
            local.add(VcsAccess::class.java, GiteaAccess(local, config, repository))
            val vcsRepository = config.template.create(local, repoPath)
            val newProject = GiteaProject(local, vcsRepository, repository.id, repository.owner.login, projectName)
            if (mapping.compute(projectKey) { _: String?, value: GiteaProject? -> if (value != null && value.projectId == repository.id) value else newProject } == newProject) {
                return newProject
            }
        }
        return null
    }

    fun removeRepository(projectName: String) {
        val projectKey = StringHelper.normalizeDir(projectName)
        val project = mapping[projectKey]
        if (project != null) {
            if (mapping.remove(projectKey, project)) {
                project.close()
            }
        }
    }
}
