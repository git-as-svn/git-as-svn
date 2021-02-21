/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import org.tmatesoft.svn.core.SVNLogEntryPath
import org.tmatesoft.svn.core.SVNNodeKind
import svnserver.repository.SvnForbiddenException
import svnserver.repository.VcsCopyFrom
import java.io.IOException
import java.util.*

/**
 * Git modification type.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitLogEntry internal constructor(val oldEntry: GitFile?, val newEntry: GitFile?) {

    @get:Throws(IOException::class)
    val change: Char
        get() {
            if (newEntry == null) return SVNLogEntryPath.TYPE_DELETED
            if (oldEntry == null) return SVNLogEntryPath.TYPE_ADDED
            if (newEntry.kind != oldEntry.kind) return SVNLogEntryPath.TYPE_REPLACED
            return if (isModified) SVNLogEntryPath.TYPE_MODIFIED else 0.toChar()
        }// By default - entry is modified.

    // Type modified.
    // Content modified.
    // Probably properties modified
    @get:Throws(IOException::class)
    val isModified: Boolean
        get() {
            try {
                if ((newEntry != null) && (oldEntry != null) && newEntry != oldEntry) {
                    // Type modified.
                    if (!Objects.equals(newEntry.fileMode, oldEntry.fileMode)) return true
                    // Content modified.
                    if ((!newEntry.isDirectory) && (!oldEntry.isDirectory)) {
                        if (!Objects.equals(newEntry.objectId, oldEntry.objectId)) return true
                    }
                    // Probably properties modified
                    val sameProperties: Boolean = (Objects.equals(newEntry.upstreamProperties, oldEntry.upstreamProperties)
                            && Objects.equals(getFilterName(newEntry), getFilterName(oldEntry)))
                    if (!sameProperties) {
                        return isPropertyModified
                    }
                }
                return false
            } catch (e: SvnForbiddenException) {
                // By default - entry is modified.
                return true
            }
        }

    @get:Throws(IOException::class)
    val isPropertyModified: Boolean
        get() {
            if ((newEntry == null) || (oldEntry == null)) return false
            val newProps: Map<String, String> = newEntry.properties
            val oldProps: Map<String, String> = oldEntry.properties
            return !Objects.equals(newProps, oldProps)
        }
    val kind: SVNNodeKind
        get() {
            if (newEntry != null) return newEntry.kind
            if (oldEntry != null) return oldEntry.kind
            throw IllegalStateException()
        }

    @get:Throws(IOException::class)
    val isContentModified: Boolean
        get() {
            if (newEntry == null || newEntry.isDirectory) return false
            if (oldEntry == null || oldEntry.isDirectory) return false
            return if (Objects.equals(filterName(newEntry), filterName(oldEntry))) {
                !Objects.equals(newEntry.objectId, oldEntry.objectId)
            } else {
                newEntry.md5 != oldEntry.md5
            }
        }

    @get:Throws(IOException::class)
    val copyFrom: VcsCopyFrom?
        get() {
            return newEntry?.copyFrom
        }

    companion object {
        private fun getFilterName(file: GitFile): String? {
            return file.filter?.name
        }

        private fun filterName(gitFile: GitFile): String? {
            return gitFile.filter?.name
        }
    }
}
