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
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNPropertyValue
import org.tmatesoft.svn.core.io.ISVNEditor
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow
import java.io.OutputStream

/**
 * Simple transparent wrapper for ISVNEditor.
 *
 * @author a.navrotskiy
 */
abstract class SVNEditorWrapper(private val editor: ISVNEditor?, private val checkDelete: Boolean) : ISVNEditor {
    // Allow removing directory entries only before creation/updating (issue #123).
    private var allowDelete = false

    @Throws(SVNException::class)
    override fun targetRevision(revision: Long) {
        editor?.targetRevision(revision)
    }

    @Throws(SVNException::class)
    override fun openRoot(revision: Long) {
        editor?.openRoot(revision)
        allowDelete = true
    }

    @Throws(SVNException::class)
    override fun deleteEntry(path: String, revision: Long) {
        editor?.deleteEntry(path, revision)
        if (checkDelete) {
            Assert.assertTrue(allowDelete, "Removing from $path#$revision is not allowed")
        }
    }

    @Throws(SVNException::class)
    override fun absentDir(path: String) {
        editor?.absentDir(path)
    }

    @Throws(SVNException::class)
    override fun absentFile(path: String) {
        editor?.absentFile(path)
    }

    @Throws(SVNException::class)
    override fun addDir(path: String, copyFromPath: String?, copyFromRevision: Long) {
        editor?.addDir(path, copyFromPath, copyFromRevision)
        allowDelete = true
    }

    @Throws(SVNException::class)
    override fun openDir(path: String, revision: Long) {
        editor?.openDir(path, revision)
        allowDelete = true
    }

    @Throws(SVNException::class)
    override fun changeDirProperty(name: String, value: SVNPropertyValue) {
        editor?.changeDirProperty(name, value)
    }

    @Throws(SVNException::class)
    override fun closeDir() {
        editor?.closeDir()
        allowDelete = false
    }

    @Throws(SVNException::class)
    override fun addFile(path: String, copyFromPath: String?, copyFromRevision: Long) {
        editor?.addFile(path, copyFromPath, copyFromRevision)
        allowDelete = false
    }

    @Throws(SVNException::class)
    override fun openFile(path: String, revision: Long) {
        editor?.openFile(path, revision)
        allowDelete = false
    }

    @Throws(SVNException::class)
    override fun changeFileProperty(path: String, propertyName: String, propertyValue: SVNPropertyValue?) {
        editor?.changeFileProperty(path, propertyName, propertyValue)
    }

    @Throws(SVNException::class)
    override fun closeFile(path: String, textChecksum: String) {
        editor?.closeFile(path, textChecksum)
    }

    @Throws(SVNException::class)
    override fun closeEdit(): SVNCommitInfo? {
        return editor?.closeEdit()
    }

    @Throws(SVNException::class)
    override fun abortEdit() {
        editor?.abortEdit()
    }

    @Throws(SVNException::class)
    override fun applyTextDelta(path: String, baseChecksum: String?) {
        editor?.applyTextDelta(path, baseChecksum)
    }

    @Throws(SVNException::class)
    override fun textDeltaChunk(path: String, diffWindow: SVNDiffWindow): OutputStream? {
        return editor?.textDeltaChunk(path, diffWindow)
    }

    @Throws(SVNException::class)
    override fun textDeltaEnd(path: String) {
        editor?.textDeltaEnd(path)
    }
}
