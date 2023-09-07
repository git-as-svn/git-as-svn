/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.replay

import org.tmatesoft.svn.core.SVNPropertyValue
import java.util.*

/**
 * ISVNEditor for comparing revisions from two repositories.
 *
 * @author a.navrotskiy
 */
class ExportSVNEditor(checkDelete: Boolean) : SVNEditorWrapper(null, checkDelete) {
    private val paths = ArrayDeque<String>()
    private val files = HashMap<String, String>()
    private val properties = HashMap<String, HashMap<String, String?>>()

    override fun openRoot(revision: Long) {
        paths.push("/")
        files["/"] = "dir"
    }

    override fun addDir(path: String, copyFromPath: String?, copyFromRevision: Long) {
        paths.push(path)
        files[path] = "directory"
    }

    override fun openDir(path: String, revision: Long) {
        paths.push(path)
        files[path] = "directory"
    }

    override fun changeDirProperty(name: String, value: SVNPropertyValue) {
        properties.computeIfAbsent(paths.element()) { HashMap() }[name] = value.string
    }

    override fun closeDir() {
        paths.pop()
    }

    override fun changeFileProperty(path: String, propertyName: String, propertyValue: SVNPropertyValue?) {
        properties.computeIfAbsent(paths.element()) { HashMap() }[propertyName] = propertyValue?.string
    }

    override fun closeFile(path: String, textChecksum: String) {
        files[path] = textChecksum
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("Files:\n")
        for ((key, value) in files) {
            sb.append("  ").append(key).append(" (").append(value).append(")\n")
            val props = properties[key]
            if (props != null) {
                for ((key1, value1) in props) {
                    sb.append("    ").append(key1).append(" = \"").append(value1).append("\"\n")
                }
            }
        }
        return sb.toString()
    }
}
