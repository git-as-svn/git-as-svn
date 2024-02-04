/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.mapping

import io.gitea.ApiClient
import io.gitea.ApiException
import io.gitea.api.RepositoryApi
import org.tmatesoft.svn.core.SVNException
import svnserver.Loggers
import svnserver.ext.gitea.mapping.DirectoryWatcher.DirectoryMapping
import java.io.IOException
import java.util.*
import java.util.concurrent.locks.ReentrantLock

internal class GiteaMapper(apiClient: ApiClient, private val mapping: GiteaMapping) : Thread(), DirectoryMapping {
    private val lock = ReentrantLock()
    private val toAdd = LinkedList<String>()
    private val toRemove = LinkedList<String>()
    private val repositoryApi: RepositoryApi = RepositoryApi(apiClient)
    override fun run() {
        try {
            while (true) {
                lock.lock()
                try {
                    run {
                        val it = toRemove.iterator()
                        while (it.hasNext()) {
                            val projectName = it.next()
                            mapping.removeRepository(projectName)
                            it.remove()
                        }
                    }
                    val it = toAdd.iterator()
                    while (it.hasNext()) {
                        val projectName = it.next()
                        val owner = projectName.substring(0, projectName.indexOf('/'))
                        val repo = projectName.substring(projectName.indexOf('/') + 1)
                        try {
                            val repository = repositoryApi.repoGet(owner, repo)
                            val project = mapping.addRepository(repository)
                            project?.initRevisions()
                            it.remove()
                        } catch (e: ApiException) {
                            // Not ready yet - try again later...
                        } catch (e: IOException) {
                            log.error("Processing error whilst adding repository: {} / {}: {}", owner, repo, e.message)
                        } catch (e: SVNException) {
                            log.error("Processing error whilst adding repository: {} / {}: {}", owner, repo, e.message)
                        }
                    }
                } finally {
                    lock.unlock()
                }
                sleep(1000)
            }
        } catch (e: InterruptedException) {
            // noop
        }
    }

    override fun addRepository(owner: String, repo: String) {
        lock.lock()
        try {
            toAdd.add("$owner/$repo")
        } finally {
            lock.unlock()
        }
    }

    override fun removeRepositories(owner: String) {
        lock.lock()
        try {
            for (project in mapping.mapping.values) {
                if (project.owner == owner) {
                    toRemove.add(project.repositoryName)
                    toAdd.remove(project.repositoryName)
                }
            }
        } finally {
            lock.unlock()
        }
    }

    override fun removeRepository(owner: String, repo: String) {
        lock.lock()
        try {
            toRemove.add("$owner/$repo")
            toAdd.remove("$owner/$repo")
        } finally {
            lock.unlock()
        }
    }

    companion object {
        private val log = Loggers.gitea
    }

    init {
        start()
    }
}
