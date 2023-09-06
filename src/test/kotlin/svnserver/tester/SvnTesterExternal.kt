/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.tester

import org.apache.commons.collections4.trie.PatriciaTrie
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager
import org.tmatesoft.svn.core.internal.util.SVNURLUtil
import org.tmatesoft.svn.core.io.SVNRepository
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import java.util.*

/**
 * External subversion server for testing.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class SvnTesterExternal(private val myUrl: SVNURL, private val authManager: ISVNAuthenticationManager?) : SvnTester {
    private val suffix: String = UUID.randomUUID().toString()

    override val url: SVNURL
        get() = myUrl.appendPath(suffix, false)

    override fun openSvnRepository(): SVNRepository {
        return openSvnRepository(url)
    }

    override fun close() {
        val repo = openSvnRepository(myUrl)
        val revision = repo.latestRevision
        try {
            val locks = repo.getLocks(suffix)
            if (locks.isNotEmpty()) {
                val root = repo.getRepositoryRoot(true)
                val locksMap = PatriciaTrie<String>()
                for (lock in locks) {
                    val relativePath = SVNURLUtil.getRelativeURL(myUrl, root.appendPath(lock.path, false), false)
                    locksMap[relativePath] = lock.id
                }
                repo.unlock(locksMap, true, null)
            }
            val editor = repo.getCommitEditor("Remove subdir for test", null, false, null)
            editor.openRoot(-1)
            editor.deleteEntry(suffix, revision)
            editor.closeEdit()
        } finally {
            repo.closeSession()
        }
    }

    private fun openSvnRepository(url: SVNURL): SVNRepository {
        val repo = SVNRepositoryFactory.create(url)
        if (authManager != null) {
            repo.authenticationManager = authManager
        }
        return repo
    }

    init {
        val repo = openSvnRepository(myUrl)
        try {
            val editor = repo.getCommitEditor("Create subdir for test", null, false, null)
            editor.openRoot(-1)
            editor.addDir(suffix, null, -1)
            editor.closeDir()
            editor.closeEdit()
        } finally {
            repo.closeSession()
        }
    }
}
