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
import org.eclipse.jgit.lib.Repository
import kotlin.math.min

/**
 * Git tree entry.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitTreeEntry constructor(val fileMode: FileMode, val objectId: GitObject<ObjectId>, val fileName: String) : Comparable<GitTreeEntry> {
    constructor(repo: Repository, fileMode: FileMode, objectId: ObjectId, fileName: String) : this(fileMode, GitObject<ObjectId>(repo, objectId), fileName)

    val id: String
        get() {
            return objectId.`object`.name
        }

    override fun compareTo(other: GitTreeEntry): Int {
        val length1: Int = fileName.length
        val length2: Int = other.fileName.length
        val length: Int = min(length1, length2) + 1
        for (i in 0 until length) {
            val c1: Char = if (i < length1) {
                fileName[i]
            } else if ((i == length1) && (fileMode === FileMode.TREE)) {
                '/'
            } else {
                0.toChar()
            }
            val c2: Char = if (i < length2) {
                other.fileName[i]
            } else if ((i == length2) && (other.fileMode === FileMode.TREE)) {
                '/'
            } else {
                0.toChar()
            }
            if (c1 != c2) {
                return c1 - c2
            }
        }
        return length1 - length2
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that: GitTreeEntry = other as GitTreeEntry
        return ((objectId == that.objectId) && (fileMode == that.fileMode) && (fileName == that.fileName))
    }

    override fun hashCode(): Int {
        var result: Int = fileMode.hashCode()
        result = 31 * result + objectId.hashCode()
        result = 31 * result + fileName.hashCode()
        return result
    }

    override fun toString(): String {
        return ("GitTreeEntry{" +
                "fileMode=" + fileMode +
                ", objectId=" + objectId +
                ", fileName='" + fileName + '\'' +
                '}')
    }

}
