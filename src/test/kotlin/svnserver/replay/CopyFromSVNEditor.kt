/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.replay

import org.tmatesoft.svn.core.io.ISVNEditor
import java.util.*

/**
 * Collect copy-from information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class CopyFromSVNEditor(editor: ISVNEditor?, private val basePath: String, checkDelete: Boolean) : SVNEditorWrapper(editor, checkDelete) {
    private val copyFrom = TreeMap<String, String>()
    override fun addDir(path: String, copyFromPath: String?, copyFromRevision: Long) {
        if (copyFromPath != null) copyFrom[basePath + path] = "$copyFromPath@$copyFromRevision"
        super.addDir(path, copyFromPath, copyFromRevision)
    }
    override fun addFile(path: String, copyFromPath: String?, copyFromRevision: Long) {
        if (copyFromPath != null) copyFrom[basePath + path] = "$copyFromPath@$copyFromRevision"
        super.addFile(path, copyFromPath, copyFromRevision)
    }

    fun getCopyFrom(): Map<String, String> {
        return copyFrom
    }
}
