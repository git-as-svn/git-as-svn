/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping

import org.tmatesoft.svn.core.SVNException
import svnserver.Loggers
import svnserver.context.LocalContext
import svnserver.repository.git.BranchProvider
import svnserver.repository.git.GitBranch
import svnserver.repository.git.GitRepository
import java.io.IOException
import java.util.*

/**
 * GitLab project information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitLabProject internal constructor(val context: LocalContext, val repository: GitRepository, val projectId: Int) : AutoCloseable, BranchProvider {

    @Volatile
    private var ready = false

    @Throws(IOException::class, SVNException::class)
    fun initRevisions() {
        if (!ready) {
            log.info("[{}]: initing...", context.name)
            for (branch in repository.branches.values) branch!!.updateRevisions()
            ready = true
        }
    }

    override fun close() {
        try {
            context.close()
        } catch (e: Exception) {
            log.error("Can't close context for repository: " + context.name, e)
        }
    }

    override val branches: NavigableMap<String, GitBranch>
        get() = if (ready) repository.branches else Collections.emptyNavigableMap()

    companion object {
        private val log = Loggers.gitlab
    }
}
