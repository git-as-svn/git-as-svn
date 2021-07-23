/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver

import org.ini4j.Reg
import org.testcontainers.DockerClientFactory
import org.testng.Assert
import org.testng.SkipException
import org.tmatesoft.svn.core.*
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil
import org.tmatesoft.svn.core.io.ISVNEditor
import org.tmatesoft.svn.core.io.SVNRepository
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Helper to testing svn repository.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
object SvnTestHelper {
    fun checkFileProp(repo: SVNRepository, filePath: String, expected: Map<String, String>) {
        val props = SVNProperties()
        repo.getFile(filePath, repo.latestRevision, props, null)
        checkProp(props, expected)
    }

    private fun checkProp(props: SVNProperties, expected: Map<String, String>) {
        val check = HashMap(expected)

        for ((key, value) in props.asMap()) {
            if (key.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) continue
            Assert.assertEquals(value.string, check.remove(key))
        }
        Assert.assertTrue(check.isEmpty())
    }

    fun checkDirProp(repo: SVNRepository, filePath: String, expected: Map<String, String>) {
        val props = SVNProperties()
        repo.getDir(filePath, repo.latestRevision, props, ArrayList<Any?>())
        checkProp(props, expected)
    }

    fun createFile(repo: SVNRepository, filePath: String, content: String, props: Map<String, String>) {
        createFile(repo, filePath, content.toByteArray(StandardCharsets.UTF_8), props)
    }

    fun createFile(repo: SVNRepository, filePath: String, content: ByteArray?, props: Map<String, String>) {
        val editor = repo.getCommitEditor("Create file: $filePath", null, false, null)
        editor.openRoot(-1)
        var index = 0
        var depth = 1
        while (true) {
            index = filePath.indexOf('/', index + 1)
            if (index < 0) {
                break
            }
            editor.openDir(filePath.substring(0, index), -1)
            depth++
        }
        editor.addFile(filePath, null, -1)
        for ((key, value) in props) {
            editor.changeFileProperty(filePath, key, SVNPropertyValue.create(value))
        }
        sendDeltaAndClose(editor, filePath, null, content)
        for (i in 0 until depth) {
            editor.closeDir()
        }
        Assert.assertNotEquals(editor.closeEdit(), SVNCommitInfo.NULL)
    }

    fun sendDeltaAndClose(editor: ISVNEditor, filePath: String, oldData: ByteArray?, newData: ByteArray?) {
        (if (oldData == null) SVNFileUtil.DUMMY_IN else ByteArrayInputStream(oldData)).use { oldStream ->
            (if (newData == null) SVNFileUtil.DUMMY_IN else ByteArrayInputStream(newData)).use { newStream ->
                editor.applyTextDelta(filePath, null)
                val deltaGenerator = SVNDeltaGenerator()
                val checksum = deltaGenerator.sendDelta(filePath, oldStream, 0, newStream, editor, true)
                editor.closeFile(filePath, checksum)
            }
        }
    }

    fun deleteFile(repo: SVNRepository, filePath: String) {
        val latestRevision = repo.latestRevision
        val editor = repo.getCommitEditor("Delete file: $filePath", null, false, null)
        editor.openRoot(-1)
        var index = 0
        var depth = 1
        while (true) {
            index = filePath.indexOf('/', index + 1)
            if (index < 0) {
                break
            }
            editor.openDir(filePath.substring(0, index), -1)
            depth++
        }
        editor.deleteEntry(filePath, latestRevision)
        for (i in 0 until depth) {
            editor.closeDir()
        }
        Assert.assertNotEquals(editor.closeEdit(), SVNCommitInfo.NULL)
    }

    fun modifyFile(repo: SVNRepository, filePath: String, newData: String, fileRev: Long, locks: Map<String, String>? = null) {
        modifyFile(repo, filePath, newData.toByteArray(StandardCharsets.UTF_8), fileRev, locks)
    }

    @JvmOverloads
    fun modifyFile(repo: SVNRepository, filePath: String, newData: ByteArray?, fileRev: Long, locks: Map<String, String>? = null) {
        val oldData = ByteArrayOutputStream()
        repo.getFile(filePath, fileRev, null, oldData)
        val editor = repo.getCommitEditor("Modify file: $filePath", locks, false, null)
        try {
            editor.openRoot(-1)
            var index = 0
            var depth = 1
            while (true) {
                index = filePath.indexOf('/', index + 1)
                if (index < 0) {
                    break
                }
                editor.openDir(filePath.substring(0, index), -1)
                depth++
            }
            editor.openFile(filePath, fileRev)
            sendDeltaAndClose(editor, filePath, oldData.toByteArray(), newData)
            for (i in 0 until depth) {
                editor.closeDir()
            }
            Assert.assertNotEquals(editor.closeEdit(), SVNCommitInfo.NULL)
        } finally {
            editor.abortEdit()
        }
    }

    fun sendDeltaAndClose(editor: ISVNEditor, filePath: String, oldData: String?, newData: String?) {
        sendDeltaAndClose(editor, filePath, oldData?.toByteArray(StandardCharsets.UTF_8), newData?.toByteArray(StandardCharsets.UTF_8))
    }

    fun checkFileContent(repo: SVNRepository, filePath: String, content: String) {
        checkFileContent(repo, filePath, content.toByteArray(StandardCharsets.UTF_8))
    }

    fun checkFileContent(repo: SVNRepository, filePath: String, content: ByteArray) {
        val info = repo.info(filePath, repo.latestRevision)
        Assert.assertEquals(info.kind, SVNNodeKind.FILE)
        Assert.assertEquals(info.size, content.size.toLong())
        ByteArrayOutputStream().use { stream ->
            repo.getFile(filePath, repo.latestRevision, null, stream)
            Assert.assertEquals(stream.toByteArray(), content)
        }
    }

    fun findExecutable(name: String): String? {
        val path = System.getenv("PATH")
        if (path != null) {
            val suffix = if (Reg.isWindows()) ".exe" else ""
            for (dir in path.split(File.pathSeparator.toRegex()).toTypedArray()) {
                val file = File(dir, name + suffix)
                if (file.exists()) {
                    return file.absolutePath
                }
            }
        }
        return null
    }

    fun skipTestIfDockerUnavailable() {
        try {
            Assert.assertNotNull(DockerClientFactory.instance().client())
        } catch (e: IllegalStateException) {
            throw SkipException("Docker is not available", e)
        }
    }

    fun skipTestIfRunningOnCI() {
        if (System.getenv("CI") != null || System.getenv("TRAVIS") != null) throw SkipException("Test is skipped because running on CI")
    }
}
