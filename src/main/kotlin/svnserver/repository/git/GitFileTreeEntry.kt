/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevTree
import org.tmatesoft.svn.core.SVNProperty
import ru.bozaro.gitlfs.common.Constants
import svnserver.repository.VcsCopyFrom
import svnserver.repository.git.filter.GitFilter
import svnserver.repository.git.prop.GitProperty
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.function.Supplier

/**
 * Git file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal class GitFileTreeEntry private constructor(
    override val branch: GitBranch,
    parentProps: Array<GitProperty>,
    parentPath: String,
    override val treeEntry: GitTreeEntry,
    override val revision: Int,
    treeEntries: Map<String, GitTreeEntry>,
) : GitEntryImpl(parentProps, parentPath, branch.repository.collectProperties(treeEntry, treeEntries), treeEntry.fileName, treeEntry.fileMode, branch.repository.context.shared.stringInterner), GitFile {

    override val filter: GitFilter = branch.repository.getFilter(treeEntry.fileMode, rawProperties)

    override val entries: SortedMap<String, Supplier<GitFile>> = treeEntries.mapValuesTo(TreeMap()) { entry ->
        when (branch.repository.gitTreeEntryCacheStrategy) {
            GitTreeEntryCacheStrategy.Lazy -> {
                val result = lazy(mode = LazyThreadSafetyMode.NONE) { create(branch, rawProperties, fullPath, entry.value, revision) }
                Supplier { result.value }
            }

            GitTreeEntryCacheStrategy.None -> {
                Supplier { create(branch, rawProperties, fullPath, entry.value, revision) }
            }
        }
    }

    override val contentHash: String
        get() {
            return filter.getContentHash(treeEntry.objectId)
        }

    @get:Throws(IOException::class)
    override val md5: String
        get() {
            return filter.getMd5(treeEntry.objectId)
        }

    @get:Throws(IOException::class)
    override val size: Long
        get() {
            return if (isDirectory) 0L else filter.getSize(treeEntry.objectId)
        }

    @Throws(IOException::class)
    override fun openStream(): InputStream {
        return filter.inputStream(treeEntry.objectId)
    }

    override val copyFrom: VcsCopyFrom?
        get() {
            return lastChange.getCopyFrom(fullPath)
        }
    override val objectId: GitObject<ObjectId>
        get() {
            return treeEntry.objectId
        }

    @get:Throws(IOException::class)
    override val properties: Map<String, String>
        get() {
            val props = upstreamProperties.toMutableMap()
            val fileMode = fileMode
            if ((fileMode == FileMode.SYMLINK)) {
                props.remove(SVNProperty.EOL_STYLE)
                props.remove(SVNProperty.MIME_TYPE)
                props[SVNProperty.SPECIAL] = "*"
            } else {
                if ((fileMode == FileMode.EXECUTABLE_FILE)) props[SVNProperty.EXECUTABLE] = "*"
                if (props.containsKey(SVNProperty.MIME_TYPE)) {
                    props.remove(SVNProperty.EOL_STYLE)
                } else if (props.containsKey(SVNProperty.EOL_STYLE)) {
                    props.remove(SVNProperty.MIME_TYPE)
                } else if (fileMode.objectType == org.eclipse.jgit.lib.Constants.OBJ_BLOB) {
                    if (branch.repository.isObjectBinary(filter, objectId)) {
                        props[SVNProperty.MIME_TYPE] = Constants.MIME_BINARY
                    } else if (branch.repository.format < RepositoryFormat.V5_REMOVE_IMPLICIT_NATIVE_EOL) {
                        props[SVNProperty.EOL_STYLE] = SVNProperty.EOL_STYLE_NATIVE
                    }
                }
            }
            return props
        }
    override val fileMode: FileMode
        get() {
            return treeEntry.fileMode
        }

    @Throws(IOException::class)
    override fun getEntry(name: String): GitFile? {
        return entries[name]?.get()
    }

    override fun hashCode(): Int {
        return (treeEntry.hashCode() + rawProperties.contentHashCode() * 31)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that: GitFileTreeEntry = other as GitFileTreeEntry
        return (Objects.equals(treeEntry, that.treeEntry) && rawProperties.contentEquals(that.rawProperties))
    }

    override fun toString(): String {
        return "GitFileInfo{fullPath='$fullPath', objectId=$treeEntry}"
    }

    companion object {
        @Throws(IOException::class)
        fun create(branch: GitBranch, tree: RevTree, revision: Int): GitFile {
            return create(branch, GitProperty.emptyArray, "", GitTreeEntry(branch.repository.git, FileMode.TREE, tree, ""), revision)
        }

        @Throws(IOException::class)
        private fun create(branch: GitBranch, parentProps: Array<GitProperty>, parentPath: String, treeEntry: GitTreeEntry, revision: Int): GitFile {
            return GitFileTreeEntry(branch, parentProps, parentPath, treeEntry, revision, branch.repository.loadTree(treeEntry))
        }
    }
}
