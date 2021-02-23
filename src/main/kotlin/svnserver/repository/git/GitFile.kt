/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.tmatesoft.svn.core.SVNNodeKind
import org.tmatesoft.svn.core.SVNProperty
import svnserver.repository.VcsCopyFrom
import svnserver.repository.git.filter.GitFilter
import svnserver.repository.git.prop.GitProperty
import java.io.IOException
import java.io.InputStream
import java.util.*

/**
 * Git file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
interface GitFile : GitEntry {
    override fun createChild(name: String, isDir: Boolean): GitEntry {
        return GitEntryImpl(rawProperties, fullPath, emptyArray(), name, if (isDir) FileMode.TREE else FileMode.REGULAR_FILE)
    }

    @Throws(IOException::class)
    override fun getEntry(name: String): GitFile?

    /**
     * Get native repository content hash for cheap content modification check.
     */
    @get:Throws(IOException::class)
    val contentHash: String
        get() {
            return md5
        }

    @get:Throws(IOException::class)
    val md5: String

    @get:Throws(IOException::class)
    val size: Long

    @Throws(IOException::class)
    fun openStream(): InputStream

    @get:Throws(IOException::class)
    val copyFrom: VcsCopyFrom?
    val filter: GitFilter?
    val treeEntry: GitTreeEntry?
    val objectId: GitObject<ObjectId>?

    @get:Throws(IOException::class)
    val allProperties: Map<String, String>
        get() {
            val props: MutableMap<String, String> = HashMap()
            props.putAll(revProperties)
            props.putAll(properties)
            return props
        }
    val revProperties: Map<String, String>
        get() {
            val props: MutableMap<String, String> = HashMap()
            val last: GitRevision = lastChange
            props[SVNProperty.UUID] = branch.uuid
            props[SVNProperty.COMMITTED_REVISION] = last.id.toString()
            putProperty(props, SVNProperty.COMMITTED_DATE, last.dateString)
            putProperty(props, SVNProperty.LAST_AUTHOR, last.author)
            return props
        }

    @get:Throws(IOException::class)
    val properties: Map<String, String>
        get() {
            return upstreamProperties
        }
    val lastChange: GitRevision
        get() {
            val branch: GitBranch = branch
            val lastChange: Int = branch.getLastChange(fullPath, revision) ?: throw IllegalStateException("Internal error: can't find lastChange revision for file: $fileName@$revision")
            return branch.sureRevisionInfo(lastChange)
        }
    val branch: GitBranch
    val upstreamProperties: Map<String, String>
        get() {
            val result: MutableMap<String, String> = HashMap()
            for (prop: GitProperty in rawProperties) {
                prop.apply(result)
            }
            return result
        }
    val revision: Int
    val isDirectory: Boolean
        get() {
            return (kind == SVNNodeKind.DIR)
        }
    val kind: SVNNodeKind
        get() {
            return when (val objType: Int = fileMode.objectType) {
                Constants.OBJ_TREE, Constants.OBJ_COMMIT -> SVNNodeKind.DIR
                Constants.OBJ_BLOB -> SVNNodeKind.FILE
                else -> throw IllegalStateException("Unknown obj type: $objType")
            }
        }
    val fileMode: FileMode

    @get:Throws(IOException::class)
    val entries: Iterable<GitFile>

    companion object {
        fun putProperty(props: MutableMap<String, String>, name: String, value: String?) {
            if (value != null) {
                props[name] = value
            }
        }
    }
}
