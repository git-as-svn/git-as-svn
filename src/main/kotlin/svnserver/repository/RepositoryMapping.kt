/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository

import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNURL
import svnserver.StringHelper
import svnserver.context.Shared
import svnserver.parser.SvnServerWriter
import svnserver.repository.git.BranchProvider
import svnserver.repository.git.GitBranch
import svnserver.server.command.BaseCmd
import java.io.IOException
import java.util.*

/**
 * Resolving repository by URL.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
interface RepositoryMapping<T : BranchProvider> : Shared {
    val mapping: NavigableMap<String, T>

    companion object {
        @Throws(SVNException::class, IOException::class)
        fun <T : BranchProvider> findRepositoryInfo(mapping: RepositoryMapping<T>, url: SVNURL, writer: SvnServerWriter): RepositoryInfo? {
            val path: String = StringHelper.normalizeDir(url.path)
            val repo: Map.Entry<String, T>? = getMapped(mapping.mapping, path)
            if (repo == null) {
                BaseCmd.sendError(writer, SVNErrorMessage.create(SVNErrorCode.RA_SVN_REPOS_NOT_FOUND, "Repository not found: $url"))
                return null
            }
            val branchPath: String = if (repo.key.isEmpty()) path else path.substring(repo.key.length - 1)
            val branches: NavigableMap<String, GitBranch> = repo.value.branches
            if (branchPath.length <= 1) {
                val branchName: String = if (repo.value.branches.size == 1) repo.value.branches.values.iterator().next().shortBranchName else "<branchname>"
                val msg: String = String.format("Repository branch not found. Use `svn relocate %s/%s` to fix your working copy", url, branchName)
                BaseCmd.sendError(writer, SVNErrorMessage.create(SVNErrorCode.RA_SVN_REPOS_NOT_FOUND, msg))
                return null
            }
            val branch: Map.Entry<String, GitBranch>? = (getMapped(branches, branchPath))!!
            if (branch == null) {
                BaseCmd.sendError(writer, SVNErrorMessage.create(SVNErrorCode.RA_SVN_REPOS_NOT_FOUND, "Repository branch not found: $url"))
                return null
            }
            return RepositoryInfo(
                SVNURL.create(
                    url.protocol,
                    url.userInfo,
                    url.host,
                    if (url.port == SVNURL.getDefaultPortNumber(url.protocol)) -1 else url.port, repo.key + branch.key.substring(1),
                    true
                ),
                branch.value
            )
        }

        fun <T> getMapped(mapping: NavigableMap<String, T>, prefix: String): Map.Entry<String, T>? {
            val path: String = StringHelper.normalizeDir(prefix)

            // TODO: this could be must faster if we find an appropriate trie implementation.
            var result: Map.Entry<String, T>? = null
            for (entry: Map.Entry<String, T> in mapping.headMap(path, true).entries) {
                if (!StringHelper.isParentPath(entry.key, path)) continue
                if (result == null || entry.key.length > result.key.length) result = entry
            }
            return result
        }
    }
}
