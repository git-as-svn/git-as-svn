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
import java.io.InputStream

/**
 * Git file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal class GitFileEmptyTree constructor(override val branch: GitBranch, parentPath: String, override val revision: Int) : GitEntryImpl(emptyArray(), parentPath, emptyArray(), "", FileMode.TREE), GitFile {
    override fun createChild(name: String, isDir: Boolean): GitEntry {
        return super<GitEntryImpl>.createChild(name, isDir)
    }

    override fun getEntry(name: String): GitFile? {
        return null
    }

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
    override val entries: Iterable<GitFile>
        get() {
            return emptyList()
        }

    override fun toString(): String {
        return "GitFileEmptyTree{fullPath='$fullPath'}"
    }
}
