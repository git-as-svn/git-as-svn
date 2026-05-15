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
import svnserver.repository.VcsCopyFrom
import svnserver.repository.git.filter.GitFilter
import svnserver.repository.git.prop.GitProperty
import java.io.InputStream
import java.util.*
import java.util.function.Supplier

/**
 * Git file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal class GitFileEmptyTree(override val branch: GitBranch, parentPath: String, override val revision: Int) : GitEntryImpl(GitProperty.emptyArray, parentPath, GitProperty.emptyArray, "", FileMode.TREE, branch.repository.context.shared.stringInterner), GitFile {
    override val contentHash: String
        get() {
            throw IllegalStateException("Can't get content hash without object.")
        }
    override val md5: String
        get() {
            throw IllegalStateException("Can't get md5 without object.")
        }
    override val size: Long
        get() {
            return 0L
        }

    override fun openStream(): InputStream {
        throw IllegalStateException("Can't get open stream without object.")
    }

    override val copyFrom: VcsCopyFrom?
        get() {
            return lastChange.getCopyFrom(fullPath)
        }
    override val filter: GitFilter?
        get() {
            return null
        }
    override val treeEntry: GitTreeEntry?
        get() {
            return null
        }
    override val objectId: GitObject<ObjectId>?
        get() {
            return null
        }
    override val fileMode: FileMode
        get() {
            return FileMode.TREE
        }

    override val entries: SortedMap<String, Supplier<GitFile>>
        get() {
            return emptyEntries
        }

    override fun toString(): String {
        return "GitFileEmptyTree{fullPath='$fullPath'}"
    }

    private companion object {
        private val emptyEntries = Collections.unmodifiableSortedMap(TreeMap<String, Supplier<GitFile>>())
    }
}
