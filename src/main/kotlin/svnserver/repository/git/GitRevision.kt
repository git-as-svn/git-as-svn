/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevCommit
import org.tmatesoft.svn.core.SVNRevisionProperty
import svnserver.StringHelper
import svnserver.SvnConstants
import svnserver.repository.VcsCopyFrom
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Git revision.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitRevision internal constructor(
    private val branch: GitBranch,
    val cacheCommit: ObjectId,
    val id: Int,
    private val renames: Map<String, VcsCopyFrom>,
    private val gitOldCommit: RevCommit?,
    val gitNewCommit: RevCommit?,
    commitTimeSec: Int
) {
    val date: Long = TimeUnit.SECONDS.toMillis(commitTimeSec.toLong())
    fun getProperties(includeInternalProps: Boolean): Map<String, String> {
        val props: MutableMap<String, String> = HashMap()
        if (includeInternalProps) {
            putProperty(props, SVNRevisionProperty.AUTHOR, author)
            putProperty(props, SVNRevisionProperty.LOG, log)
            putProperty(props, SVNRevisionProperty.DATE, dateString)
        }
        if (gitNewCommit != null) {
            props[SvnConstants.PROP_GIT] = gitNewCommit.name()
        }
        return props
    }

    private fun putProperty(props: MutableMap<String, String>, name: String, value: String?) {
        if (value != null) {
            props[name] = value
        }
    }

    val author: String?
        get() {
            if (gitNewCommit == null) return null
            val ident: PersonIdent = gitNewCommit.authorIdent
            return String.format("%s <%s>", ident.name, ident.emailAddress)
        }
    val log: String?
        get() {
            return gitNewCommit?.fullMessage?.trim { it <= ' ' }
        }
    val dateString: String
        get() {
            return StringHelper.formatDate(date)
        }

    @Throws(IOException::class)
    fun getFile(fullPath: String): GitFile? {
        if (gitNewCommit == null) {
            return if (fullPath.isEmpty()) GitFileEmptyTree(branch, "", id) else null
        }
        var result: GitFile? = GitFileTreeEntry.create(branch, gitNewCommit.tree, id)
        for (pathItem: String in fullPath.split("/").toTypedArray()) {
            if (pathItem.isEmpty()) {
                continue
            }
            result = result!!.getEntry(pathItem)
            if (result == null) {
                return null
            }
        }
        return result
    }

    @get:Throws(IOException::class)
    val changes: Map<String, GitLogEntry>
        get() {
            if (gitNewCommit == null) {
                return emptyMap()
            }
            val oldTree: GitFile = if (gitOldCommit == null) GitFileEmptyTree(branch, "", id - 1) else GitFileTreeEntry.create(branch, gitOldCommit.tree, id - 1)
            val newTree: GitFile = GitFileTreeEntry.create(branch, gitNewCommit.tree, id)
            return ChangeHelper.collectChanges(oldTree, newTree, false)
        }

    fun getCopyFrom(fullPath: String): VcsCopyFrom? {
        return renames[fullPath]
    }
}
