/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server

import com.google.common.base.Strings
import org.testng.Assert
import org.testng.annotations.Test
import org.tmatesoft.svn.core.SVNLogEntry
import org.tmatesoft.svn.core.SVNProperty
import org.tmatesoft.svn.core.SVNPropertyValue
import org.tmatesoft.svn.core.io.SVNRepository
import svnserver.SvnTestHelper
import svnserver.SvnTestHelper.modifyFile
import svnserver.SvnTestServer
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.zip.GZIPOutputStream

/**
 * Check file content filter.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class SvnFilterTest {
    /**
     * Check file read content on filter change.
     */
    @Test
    fun binaryRead() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            val uncompressed = "Test file\u0000".toByteArray(StandardCharsets.UTF_8)
            val compressed = gzip(uncompressed)

            // Add compressed file to repository.
            SvnTestHelper.createFile(repo, "/data.z", compressed, SvnFilePropertyTest.propsBinary)
            SvnTestHelper.createFile(repo, "/data.x", compressed, SvnFilePropertyTest.propsBinary)
            SvnTestHelper.checkFileProp(repo, "/data.z", SvnFilePropertyTest.propsBinary)
            SvnTestHelper.checkFileProp(repo, "/data.x", SvnFilePropertyTest.propsBinary)
            SvnTestHelper.checkFileContent(repo, "/data.z", compressed)
            SvnTestHelper.checkFileContent(repo, "/data.x", compressed)
            // Add filter to file.
            SvnTestHelper.createFile(repo, "/.gitattributes", "*.z\t\t\tfilter=gzip\n", SvnFilePropertyTest.propsEolNative)
            // On file read now we must have uncompressed content.
            SvnTestHelper.checkFileProp(repo, "/data.z", SvnFilePropertyTest.propsBinary)
            SvnTestHelper.checkFileProp(repo, "/data.x", SvnFilePropertyTest.propsBinary)
            SvnTestHelper.checkFileContent(repo, "/data.z", uncompressed)
            SvnTestHelper.checkFileContent(repo, "/data.x", compressed)
            // Modify filter.
            modifyFile(repo, "/.gitattributes", "*.x\t\t\tfilter=gzip\n", repo.latestRevision)
            // Check result.
            SvnTestHelper.checkFileProp(repo, "/data.z", SvnFilePropertyTest.propsBinary)
            SvnTestHelper.checkFileProp(repo, "/data.x", SvnFilePropertyTest.propsBinary)
            SvnTestHelper.checkFileContent(repo, "/data.z", compressed)
            SvnTestHelper.checkFileContent(repo, "/data.x", uncompressed)
        }
    }

    /**
     * Check file read content on filter change.
     */
    @Test
    fun textRead() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            val uncompressed = "Test file".toByteArray(StandardCharsets.UTF_8)
            val compressed = gzip(uncompressed)

            // Add compressed file to repository.
            SvnTestHelper.createFile(repo, "/data.z", compressed, SvnFilePropertyTest.propsBinary)
            SvnTestHelper.createFile(repo, "/data.x", compressed, SvnFilePropertyTest.propsBinary)
            SvnTestHelper.checkFileProp(repo, "/data.z", SvnFilePropertyTest.propsBinary)
            SvnTestHelper.checkFileProp(repo, "/data.x", SvnFilePropertyTest.propsBinary)
            SvnTestHelper.checkFileContent(repo, "/data.z", compressed)
            SvnTestHelper.checkFileContent(repo, "/data.x", compressed)
            // Add filter to file.
            SvnTestHelper.createFile(repo, "/.gitattributes", "*.z\t\t\tfilter=gzip\n", SvnFilePropertyTest.propsEolNative)
            // After commit .gitattributes file data.z must change property svn:mime-type and content automagically.
            run {
                val changed = HashSet<String>()
                repo.log(arrayOf(""), repo.latestRevision, repo.latestRevision, true, false) { logEntry: SVNLogEntry -> changed.addAll(logEntry.changedPaths.keys) }
                Assert.assertTrue(changed.contains("/.gitattributes"))
                Assert.assertTrue(changed.contains("/data.z"))
                Assert.assertEquals(changed.size, 2)
            }
            // On file read now we must have uncompressed content.
            SvnTestHelper.checkFileProp(repo, "/data.z", SvnFilePropertyTest.propsEolNative)
            SvnTestHelper.checkFileProp(repo, "/data.x", SvnFilePropertyTest.propsBinary)
            SvnTestHelper.checkFileContent(repo, "/data.z", uncompressed)
            SvnTestHelper.checkFileContent(repo, "/data.x", compressed)
            // Modify filter.
            modifyFile(repo, "/.gitattributes", "*.x\t\t\tfilter=gzip\n", repo.latestRevision)
            // After commit .gitattributes file data.z must change property svn:mime-type and content automagically.
            run {
                val changed = HashSet<String>()
                repo.log(arrayOf(""), repo.latestRevision, repo.latestRevision, true, false) { logEntry: SVNLogEntry -> changed.addAll(logEntry.changedPaths.keys) }
                Assert.assertTrue(changed.contains("/.gitattributes"))
                Assert.assertTrue(changed.contains("/data.z"))
                Assert.assertTrue(changed.contains("/data.x"))
                Assert.assertEquals(changed.size, 3)
            }
            // Check result.
            SvnTestHelper.checkFileProp(repo, "/data.z", SvnFilePropertyTest.propsBinary)
            SvnTestHelper.checkFileProp(repo, "/data.x", SvnFilePropertyTest.propsEolNative)
            SvnTestHelper.checkFileContent(repo, "/data.z", compressed)
            SvnTestHelper.checkFileContent(repo, "/data.x", uncompressed)
        }
    }

    /**
     * Write filtered file.
     */
    @Test
    fun write() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()

            // Add filter to file.
            SvnTestHelper.createFile(repo, "/.gitattributes", "/*.z\t\t\tfilter=gzip\n", SvnFilePropertyTest.propsEolNative)
            // On file read now we must have uncompressed content.
            SvnTestHelper.createFile(repo, "/data.z", CONTENT_FOO, SvnFilePropertyTest.propsEolNative)
            SvnTestHelper.checkFileContent(repo, "/data.z", CONTENT_FOO)
            // Modify file.
            modifyFile(repo, "/data.z", CONTENT_BAR, repo.latestRevision)
            SvnTestHelper.checkFileContent(repo, "/data.z", CONTENT_BAR)
        }
    }

    /**
     * Write file before .gitattributes in single commit.
     */
    @Test
    fun writeBeforeAttributes() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()

            // Create file.
            run {
                val editor = repo.getCommitEditor("Complex commit", null, false, null)
                editor.openRoot(-1)
                editor.addFile("data.z", null, -1)
                editor.changeFileProperty("data.z", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
                SvnTestHelper.sendDeltaAndClose(editor, "data.z", null, CONTENT_FOO)
                editor.addFile(".gitattributes", null, -1)
                editor.changeFileProperty(".gitattributes", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
                SvnTestHelper.sendDeltaAndClose(editor, ".gitattributes", null, "*.z\t\t\tfilter=gzip\n")
                editor.closeDir()
                editor.closeEdit()
            }
            // On file read now we must have uncompressed content.
            SvnTestHelper.checkFileContent(repo, "/data.z", CONTENT_FOO)

            // Modify file.
            run {
                val rev = repo.latestRevision
                val editor = repo.getCommitEditor("Complex commit", null, false, null)
                editor.openRoot(-1)
                editor.openFile("data.z", rev)
                SvnTestHelper.sendDeltaAndClose(editor, "data.z", CONTENT_FOO, CONTENT_BAR)
                editor.openFile(".gitattributes", rev)
                SvnTestHelper.sendDeltaAndClose(editor, ".gitattributes", "*.z\t\t\tfilter=gzip\n", "")
                editor.closeDir()
                editor.closeEdit()
            }
            // On file read now we must have uncompressed content.
            SvnTestHelper.checkFileContent(repo, "/data.z", CONTENT_BAR)
        }
    }

    /**
     * Write file after .gitattributes in single commit.
     */
    @Test
    fun writeAfterAttributes() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()

            // Create file.
            run {
                val editor = repo.getCommitEditor("Complex commit", null, false, null)
                editor.openRoot(-1)
                editor.addFile(".gitattributes", null, -1)
                editor.changeFileProperty(".gitattributes", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
                SvnTestHelper.sendDeltaAndClose(editor, ".gitattributes", null, "*.z\t\t\tfilter=gzip\n")
                editor.addFile("data.z", null, -1)
                editor.changeFileProperty("data.z", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
                SvnTestHelper.sendDeltaAndClose(editor, "data.z", null, CONTENT_FOO)
                editor.closeDir()
                editor.closeEdit()
            }
            // On file read now we must have uncompressed content.
            SvnTestHelper.checkFileContent(repo, "/data.z", CONTENT_FOO)

            // Modify file.
            run {
                val rev = repo.latestRevision
                val editor = repo.getCommitEditor("Complex commit", null, false, null)
                editor.openRoot(-1)
                editor.openFile(".gitattributes", rev)
                SvnTestHelper.sendDeltaAndClose(editor, ".gitattributes", "*.z\t\t\tfilter=gzip\n", "")
                editor.openFile("data.z", rev)
                SvnTestHelper.sendDeltaAndClose(editor, "data.z", CONTENT_FOO, CONTENT_BAR)
                editor.closeDir()
                editor.closeEdit()
            }
            // On file read now we must have uncompressed content.
            SvnTestHelper.checkFileContent(repo, "/data.z", CONTENT_BAR)
        }
    }

    /**
     * Copy file with filter change.
     */
    @Test
    fun copy() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()

            // Add filter to file.
            SvnTestHelper.createFile(repo, "/.gitattributes", "/*.z\t\t\tfilter=gzip\n", SvnFilePropertyTest.propsEolNative)
            // Create source file.
            SvnTestHelper.createFile(repo, "/data.txt", CONTENT_FOO, SvnFilePropertyTest.propsEolNative)
            // Copy source file with "raw" filter to destination with "gzip" filter.
            run {
                val rev = repo.latestRevision
                val editor = repo.getCommitEditor("Copy file commit", null, false, null)
                editor.openRoot(-1)
                editor.addFile("data.z", "data.txt", rev)
                editor.closeFile("data.z", null)
                editor.closeDir()
                editor.closeEdit()
            }
            // On file read now we must have uncompressed content.
            SvnTestHelper.checkFileContent(repo, "/data.z", CONTENT_FOO)
        }
    }

    /**
     * Copy file with filter change.
     */
    @Test
    fun copyAndModify() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()

            // Add filter to file.
            SvnTestHelper.createFile(repo, "/.gitattributes", "/*.z\t\t\tfilter=gzip\n", SvnFilePropertyTest.propsEolNative)
            // Create source file.
            SvnTestHelper.createFile(repo, "/data.txt", CONTENT_FOO, SvnFilePropertyTest.propsEolNative)
            // Copy source file with "raw" filter to destination with "gzip" filter.
            run {
                val rev = repo.latestRevision
                val editor = repo.getCommitEditor("Copy file commit", null, false, null)
                editor.openRoot(-1)
                editor.addFile("data.z", "data.txt", rev)
                SvnTestHelper.sendDeltaAndClose(editor, "data.z", CONTENT_FOO, CONTENT_BAR)
                editor.closeDir()
                editor.closeEdit()
            }
            // On file read now we must have uncompressed content.
            SvnTestHelper.checkFileContent(repo, "/data.z", CONTENT_BAR)
        }
    }

    companion object {
        private val CONTENT_FOO = """${Strings.repeat("Some data\n", 100)}Foo file
""".toByteArray(StandardCharsets.UTF_8)
        private val CONTENT_BAR = """${Strings.repeat("Some data\n", 100)}Bar file
""".toByteArray(StandardCharsets.UTF_8)

        private fun gzip(data: ByteArray): ByteArray {
            val result = ByteArrayOutputStream()
            GZIPOutputStream(result).use { stream -> stream.write(data) }
            return result.toByteArray()
        }
    }
}
