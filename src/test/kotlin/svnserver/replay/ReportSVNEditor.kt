/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.replay

import org.testng.Assert
import org.tmatesoft.svn.core.SVNCommitInfo
import org.tmatesoft.svn.core.SVNPropertyValue
import org.tmatesoft.svn.core.io.ISVNEditor
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow
import svnserver.StringHelper.parentDir
import java.io.OutputStream
import java.util.*

/**
 * ISVNEditor for comparing differ subversion servers behaviour.
 *
 * @author a.navrotskiy
 */
class ReportSVNEditor : ISVNEditor {
    private val paths: Deque<String> = ArrayDeque()
    private val caseChecker: MutableSet<String> = HashSet()
    private val report: MutableSet<String> = TreeSet()
    private var targetRevision: Long = 0
    override fun targetRevision(revision: Long) {
        targetRevision = revision
    }

    override fun openRoot(revision: Long) {
        paths.push("/")
        add("", "open-root: " + rev(revision))
    }

    override fun deleteEntry(path: String, revision: Long) {
        del(path, "delete-entry: " + rev(revision))
    }

    override fun absentDir(path: String) {
        add(path, "absent-dir")
    }

    override fun absentFile(path: String) {
        add(path, "absent-file")
    }

    override fun addDir(path: String, copyFromPath: String?, copyFromRevision: Long) {
        paths.push(path)
        if (copyFromPath != null) {
            add("add-dir: " + copyFromPath + ", " + rev(copyFromRevision))
        } else {
            add("add-dir")
        }
    }

    override fun openDir(path: String, revision: Long) {
        paths.push(path)
        add("open-dir: " + rev(revision))
    }

    override fun changeDirProperty(name: String, value: SVNPropertyValue?) {
        add("change-dir-prop: " + name + if (value == null) " (removed)" else "")
    }

    override fun closeDir() {
        paths.pop()
    }

    override fun addFile(path: String, copyFromPath: String?, copyFromRevision: Long) {
        if (copyFromPath != null) {
            add(path, "add-file: " + copyFromPath + ", " + rev(copyFromRevision))
        } else {
            add(path, "add-file")
        }
    }

    override fun openFile(path: String, revision: Long) {
        add(path, "open-file: " + rev(revision))
    }

    override fun changeFileProperty(path: String, name: String, value: SVNPropertyValue?) {
        add(path, "change-file-prop: " + name + if (value == null) " (removed)" else "")
    }

    override fun closeFile(path: String, textChecksum: String) {
        add(path, "close-file: $textChecksum")
    }

    override fun closeEdit(): SVNCommitInfo? {
        return null
    }

    override fun abortEdit() {
        add("/", "abort-edit")
    }

    private fun add(line: String) {
        add(paths.first, line)
    }

    private fun del(path: String, line: String) {
        Assert.assertFalse(caseChecker.contains(parentDir(path)), "Remove after modification: $path")
        report.add("$path - $line")
    }

    private fun add(path: String, line: String) {
        caseChecker.add(parentDir(path))
        report.add("$path - $line")
    }

    private fun rev(revision: Long): String {
        return if (revision < 0) {
            "rN"
        } else "r" + (targetRevision - revision)
    }

    override fun applyTextDelta(path: String, baseChecksum: String?) {
        add(path, "apply-text-delta: $baseChecksum")
    }

    override fun textDeltaChunk(path: String, diffWindow: SVNDiffWindow): OutputStream? {
        add(path, "delta-chunk")
        return null
    }

    override fun textDeltaEnd(path: String) {
        add(path, "delta-end")
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (line in report) {
            sb.append(line).append("\n")
        }
        return sb.toString()
    }
}
