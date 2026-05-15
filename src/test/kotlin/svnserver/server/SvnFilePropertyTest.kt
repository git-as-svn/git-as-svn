/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server

import org.testng.Assert
import org.testng.annotations.Test
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNLogEntry
import org.tmatesoft.svn.core.SVNProperty
import org.tmatesoft.svn.core.SVNPropertyValue
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil
import org.tmatesoft.svn.core.io.SVNRepository
import svnserver.SvnTestHelper
import svnserver.SvnTestServer

/**
 * Check file properties.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class SvnFilePropertyTest {
    /**
     * Check commit .gitattributes.
     */
    @Test
    fun executable() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/non-exec.txt", "", emptyMap())
            SvnTestHelper.createFile(repo, "/exec.txt", "", propsExecutable)
            SvnTestHelper.checkFileProp(repo, "/non-exec.txt", emptyMap())
            SvnTestHelper.checkFileProp(repo, "/exec.txt", propsExecutable)
            run {
                val latestRevision = repo.latestRevision
                val editor = repo.getCommitEditor("Create directory: /foo", null, false, null)
                editor.openRoot(-1)
                editor.openFile("/non-exec.txt", latestRevision)
                editor.changeFileProperty("/non-exec.txt", SVNProperty.EXECUTABLE, SVNPropertyValue.create("*"))
                editor.closeFile("/non-exec.txt", null)
                editor.openFile("/exec.txt", latestRevision)
                editor.changeFileProperty("/exec.txt", SVNProperty.EXECUTABLE, null)
                editor.closeFile("/exec.txt", null)
                editor.closeDir()
                editor.closeEdit()
            }
            SvnTestHelper.checkFileProp(repo, "/non-exec.txt", propsExecutable)
            SvnTestHelper.checkFileProp(repo, "/exec.txt", emptyMap())
        }
    }

    /**
     * Check commit .gitattributes.
     */
    @Test
    fun binary() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/data.txt", "Test file", emptyMap())
            SvnTestHelper.createFile(repo, "/data.dat", "Test data\u0000", propsBinary)
            SvnTestHelper.checkFileProp(repo, "/data.txt", emptyMap())
            SvnTestHelper.checkFileProp(repo, "/data.dat", propsBinary)
            run {
                val latestRevision = repo.latestRevision
                val editor = repo.getCommitEditor("Modify files", null, false, null)
                editor.openRoot(-1)
                editor.openFile("/data.txt", latestRevision)
                editor.changeFileProperty("/data.txt", SVNProperty.MIME_TYPE, SVNPropertyValue.create(SVNFileUtil.BINARY_MIME_TYPE))
                editor.changeFileProperty("/data.txt", SVNProperty.EOL_STYLE, null)
                SvnTestHelper.sendDeltaAndClose(editor, "/data.txt", "Test file", "Test file\u0000")
                editor.openFile("/data.dat", latestRevision)
                editor.changeFileProperty("/data.dat", SVNProperty.MIME_TYPE, null)
                SvnTestHelper.sendDeltaAndClose(editor, "/data.dat", "Test data\u0000", "Test data")
                editor.closeDir()
                editor.closeEdit()
            }
            SvnTestHelper.checkFileProp(repo, "/data.txt", propsBinary)
            SvnTestHelper.checkFileProp(repo, "/data.dat", emptyMap())
        }
    }

    /**
     * Check commit .gitattributes.
     */
    @Test
    fun symlink() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            val content = "link foo/bar.txt"
            SvnTestHelper.createFile(repo, "/non-link", content, emptyMap())
            SvnTestHelper.createFile(repo, "/link", content, propsSymlink)
            SvnTestHelper.checkFileProp(repo, "/non-link", emptyMap())
            SvnTestHelper.checkFileProp(repo, "/link", propsSymlink)
            SvnTestHelper.checkFileContent(repo, "/non-link", content)
            SvnTestHelper.checkFileContent(repo, "/link", content)
            val content2 = "link bar/foo.txt"
            run {
                val latestRevision = repo.latestRevision
                val editor = repo.getCommitEditor("Change symlink property", null, false, null)
                editor.openRoot(-1)
                editor.openFile("/non-link", latestRevision)
                editor.changeFileProperty("/non-link", SVNProperty.SPECIAL, SVNPropertyValue.create("*"))
                SvnTestHelper.sendDeltaAndClose(editor, "/non-link", content, content2)
                editor.openFile("/link", latestRevision)
                editor.changeFileProperty("/link", SVNProperty.SPECIAL, null)
                SvnTestHelper.sendDeltaAndClose(editor, "/link", content, content2)
                editor.closeDir()
                editor.closeEdit()
            }
            SvnTestHelper.checkFileProp(repo, "/non-link", propsSymlink)
            SvnTestHelper.checkFileProp(repo, "/link", emptyMap())
            SvnTestHelper.checkFileContent(repo, "/non-link", content2)
            SvnTestHelper.checkFileContent(repo, "/link", content2)
            run {
                val latestRevision = repo.latestRevision
                val editor = repo.getCommitEditor("Change symlink property", null, false, null)
                editor.openRoot(-1)
                editor.openFile("/non-link", latestRevision)
                editor.changeFileProperty("/non-link", SVNProperty.SPECIAL, null)
                editor.closeFile("/non-link", null)
                editor.openFile("/link", latestRevision)
                editor.changeFileProperty("/link", SVNProperty.SPECIAL, SVNPropertyValue.create("*"))
                editor.closeFile("/link", null)
                editor.closeDir()
                editor.closeEdit()
            }
            SvnTestHelper.checkFileProp(repo, "/non-link", emptyMap())
            SvnTestHelper.checkFileProp(repo, "/link", propsSymlink)
            SvnTestHelper.checkFileContent(repo, "/non-link", content2)
            SvnTestHelper.checkFileContent(repo, "/link", content2)
        }
    }

    @Test
    fun native() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            val content = "foo"
            SvnTestHelper.createFile(repo, "/text.txt", content, emptyMap())
            SvnTestHelper.createFile(repo, "/.gitattributes", "*.txt text", emptyMap())
            SvnTestHelper.checkFileProp(repo, "/text.txt", propsEolNative)
        }
    }

    @Test
    fun crlf() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            val content = "foo"
            SvnTestHelper.createFile(repo, "/text.txt", content, emptyMap())
            SvnTestHelper.createFile(repo, "/.gitattributes", "*.txt eol=crlf", emptyMap())
            SvnTestHelper.checkFileProp(repo, "/text.txt", propsEolCrLf)
        }
    }

    @Test
    fun symlinkBinary() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            val content = "link foo/bar.txt"
            SvnTestHelper.createFile(repo, "/.gitattributes", "*.bin binary", emptyMap())
            SvnTestHelper.createFile(repo, "/non-link.bin", content, propsBinary)
            SvnTestHelper.createFile(repo, "/link.bin", content, propsSymlink)
            SvnTestHelper.checkFileProp(repo, "/non-link.bin", propsBinary)
            SvnTestHelper.checkFileProp(repo, "/link.bin", propsSymlink)
            SvnTestHelper.checkFileContent(repo, "/non-link.bin", content)
            SvnTestHelper.checkFileContent(repo, "/link.bin", content)
        }
    }

    @Test
    fun needsLock() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            val content = "link foo/bar.txt"
            SvnTestHelper.createFile(repo, "/link.bin", content, emptyMap())
            SvnTestHelper.createFile(repo, "/.gitattributes", "*.bin lockable", emptyMap())
            SvnTestHelper.checkFileProp(repo, "/link.bin", propsNeedsLock)
        }
    }

    /**
     * Check commit .gitattributes.
     */
    @Test
    fun commitUpdatePropertiesRoot() {
        //Map<String, String> props = new HashMap<>()["key":""];
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/sample.txt", "", emptyMap())
            SvnTestHelper.checkFileProp(repo, "/sample.txt", emptyMap())
            SvnTestHelper.createFile(repo, "/.gitattributes", "*.txt\t\t\ttext eol=lf\n", emptyMap())
            // After commit .gitattributes file sample.txt must change property svn:eol-style automagically.
            SvnTestHelper.checkFileProp(repo, "/sample.txt", propsEolLf)
            // After commit .gitattributes directory with .gitattributes must change property svn:auto-props automagically.
            SvnTestHelper.checkDirProp(repo, "/", propsAutoProps)
            // After commit .gitattributes file sample.txt must change property svn:eol-style automagically.
            run {
                val changed = HashSet<String>()
                repo.log(arrayOf(""), repo.latestRevision, repo.latestRevision, true, false) { logEntry: SVNLogEntry -> changed.addAll(logEntry.changedPaths.keys) }
                Assert.assertTrue(changed.contains("/"))
                Assert.assertTrue(changed.contains("/.gitattributes"))
                Assert.assertTrue(changed.contains("/sample.txt"))
                Assert.assertEquals(changed.size, 3)
            }
        }
    }

    /**
     * Check commit .gitattributes.
     */
    @Test
    fun commitUpdatePropertiesSubdir() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            run {
                val editor = repo.getCommitEditor("Create directory: /foo", null, false, null)
                editor.openRoot(-1)
                editor.addDir("/foo", null, -1)
                // Empty file.
                val emptyFile = "/foo/.keep"
                editor.addFile(emptyFile, null, -1)
                SvnTestHelper.sendDeltaAndClose(editor, emptyFile, null, "")
                // Close dir
                editor.closeDir()
                editor.closeDir()
                editor.closeEdit()
            }
            SvnTestHelper.createFile(repo, "/sample.txt", "", emptyMap())
            SvnTestHelper.createFile(repo, "/foo/sample.txt", "", emptyMap())
            SvnTestHelper.checkFileProp(repo, "/sample.txt", emptyMap())
            SvnTestHelper.checkFileProp(repo, "/foo/sample.txt", emptyMap())
            SvnTestHelper.createFile(repo, "/foo/.gitattributes", "*.txt\t\t\ttext eol=lf\n", emptyMap())
            // After commit .gitattributes file sample.txt must change property svn:eol-style automagically.
            SvnTestHelper.checkFileProp(repo, "/foo/sample.txt", propsEolLf)
            SvnTestHelper.checkFileProp(repo, "/sample.txt", emptyMap())
            // After commit .gitattributes directory with .gitattributes must change property svn:auto-props automagically.
            SvnTestHelper.checkDirProp(repo, "/foo", propsAutoProps)
            // After commit .gitattributes file sample.txt must change property svn:eol-style automagically.
            run {
                val changed = HashSet<String>()
                repo.log(arrayOf(""), repo.latestRevision, repo.latestRevision, true, false) { logEntry: SVNLogEntry -> changed.addAll(logEntry.changedPaths.keys) }
                Assert.assertTrue(changed.contains("/foo"))
                Assert.assertTrue(changed.contains("/foo/.gitattributes"))
                Assert.assertTrue(changed.contains("/foo/sample.txt"))
                Assert.assertEquals(changed.size, 3)
            }
        }
    }

    /**
     * Check commit .gitattributes.
     */
    @Test
    fun commitDirWithProperties() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            val latestRevision = repo.latestRevision
            val editor = repo.getCommitEditor("Create directory: /foo", null, false, null)
            editor.openRoot(-1)
            editor.addDir("/foo", null, latestRevision)
            editor.changeDirProperty(SVNProperty.INHERITABLE_AUTO_PROPS, SVNPropertyValue.create("*.txt = svn:eol-style=native\n"))
            // Empty file.
            val filePath = "/foo/.gitattributes"
            editor.addFile(filePath, null, -1)
            SvnTestHelper.sendDeltaAndClose(editor, filePath, null, "*.txt\t\t\ttext\n")
            // Close dir
            editor.closeDir()
            editor.closeDir()
            editor.closeEdit()
        }
    }

    /**
     * Check commit .gitattributes.
     */
    @Test
    fun commitDirWithoutProperties() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            try {
                val latestRevision = repo.latestRevision
                val editor = repo.getCommitEditor("Create directory: /foo", null, false, null)
                editor.openRoot(-1)
                editor.addDir("/foo", null, latestRevision)
                // Empty file.
                val filePath = "/foo/.gitattributes"
                editor.addFile(filePath, null, -1)
                SvnTestHelper.sendDeltaAndClose(editor, filePath, null, "*.txt\t\t\ttext\n")
                // Close dir
                editor.closeDir()
                editor.closeDir()
                editor.closeEdit()
                Assert.fail()
            } catch (e: SVNException) {
                Assert.assertTrue(e.message!!.contains(SVNProperty.INHERITABLE_AUTO_PROPS))
            }
        }
    }

    /**
     * Check commit .gitattributes.
     */
    @Test
    fun commitDirUpdateWithProperties() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            run {
                val editor = repo.getCommitEditor("Create directory: /foo", null, false, null)
                editor.openRoot(-1)
                editor.addDir("/foo", null, -1)
                // Empty file.
                val filePath = "/foo/.gitattributes"
                editor.addFile(filePath, null, -1)
                SvnTestHelper.sendDeltaAndClose(editor, filePath, null, "")
                // Close dir
                editor.closeDir()
                editor.closeDir()
                editor.closeEdit()
            }
            run {
                val latestRevision = repo.latestRevision
                val editor = repo.getCommitEditor("Modify .gitattributes", null, false, null)
                editor.openRoot(-1)
                editor.openDir("/foo", latestRevision)
                editor.changeDirProperty(SVNProperty.INHERITABLE_AUTO_PROPS, SVNPropertyValue.create("*.txt = svn:eol-style=native\n"))
                // Empty file.
                val filePath = "/foo/.gitattributes"
                editor.openFile(filePath, latestRevision)
                SvnTestHelper.sendDeltaAndClose(editor, filePath, "", "*.txt\t\t\ttext\n")
                // Close dir
                editor.closeDir()
                editor.closeDir()
                editor.closeEdit()
            }
        }
    }

    /**
     * Check commit .gitattributes.
     */
    @Test
    fun commitDirUpdateWithoutProperties() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            run {
                val editor = repo.getCommitEditor("Create directory: /foo", null, false, null)
                editor.openRoot(-1)
                editor.addDir("/foo", null, -1)
                // Empty file.
                val filePath = "/foo/.gitattributes"
                editor.addFile(filePath, null, -1)
                SvnTestHelper.sendDeltaAndClose(editor, filePath, null, "")
                // Close dir
                editor.closeDir()
                editor.closeDir()
                editor.closeEdit()
            }
            try {
                val latestRevision = repo.latestRevision
                val editor = repo.getCommitEditor("Modify .gitattributes", null, false, null)
                editor.openRoot(-1)
                editor.openDir("/foo", latestRevision)
                // Empty file.
                val filePath = "/foo/.gitattributes"
                editor.openFile(filePath, latestRevision)
                SvnTestHelper.sendDeltaAndClose(editor, filePath, "", "*.txt\t\t\ttext\n")
                // Close dir
                editor.closeDir()
                editor.closeDir()
                editor.closeEdit()
                Assert.fail()
            } catch (e: SVNException) {
                Assert.assertTrue(e.message!!.contains(SVNProperty.INHERITABLE_AUTO_PROPS))
            }
        }
    }

    /**
     * Check commit .gitattributes.
     */
    @Test
    fun commitRootWithProperties() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/.gitattributes", "", emptyMap())
            run {
                val latestRevision = repo.latestRevision
                val editor = repo.getCommitEditor("Modify .gitattributes", null, false, null)
                editor.openRoot(latestRevision)
                editor.changeDirProperty(SVNProperty.INHERITABLE_AUTO_PROPS, SVNPropertyValue.create("*.txt = svn:eol-style=native\n"))
                // Empty file.
                val filePath = "/.gitattributes"
                editor.openFile(filePath, latestRevision)
                SvnTestHelper.sendDeltaAndClose(editor, filePath, "", "*.txt\t\t\ttext\n")
                // Close dir
                editor.closeDir()
                editor.closeEdit()
            }
        }
    }

    /**
     * Check commit .gitattributes.
     */
    @Test
    fun commitRootWithoutProperties() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/.gitattributes", "", emptyMap())
            try {
                val latestRevision = repo.latestRevision
                val editor = repo.getCommitEditor("Modify .gitattributes", null, false, null)
                editor.openRoot(latestRevision)
                // Empty file.
                val filePath = "/.gitattributes"
                editor.openFile(filePath, latestRevision)
                SvnTestHelper.sendDeltaAndClose(editor, filePath, "", "*.txt\t\t\ttext\n")
                // Close dir
                editor.closeDir()
                editor.closeEdit()
                Assert.fail()
            } catch (e: SVNException) {
                Assert.assertTrue(e.message!!.contains(SVNProperty.INHERITABLE_AUTO_PROPS))
            }
        }
    }

    /**
     * Check commit .gitattributes.
     */
    @Test
    fun commitFileWithProperties() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "sample.txt", "", emptyMap())
            SvnTestHelper.checkFileProp(repo, "/sample.txt", emptyMap())
            SvnTestHelper.createFile(repo, ".gitattributes", "*.txt\t\t\ttext eol=lf\n", emptyMap())
            SvnTestHelper.createFile(repo, "with-props.txt", "", propsEolLf)
            try {
                SvnTestHelper.createFile(repo, "none-props.txt", "", emptyMap())
            } catch (e: SVNException) {
                Assert.assertTrue(e.message!!.contains(SVNProperty.EOL_STYLE))
            }
        }
    }

    companion object {
        val propsBinary = hashMapOf(SVNProperty.MIME_TYPE to SVNFileUtil.BINARY_MIME_TYPE)
        val propsEolNative = hashMapOf(SVNProperty.EOL_STYLE to SVNProperty.EOL_STYLE_NATIVE)
        val propsEolLf = hashMapOf(SVNProperty.EOL_STYLE to SVNProperty.EOL_STYLE_LF)
        val propsEolCrLf = hashMapOf(SVNProperty.EOL_STYLE to SVNProperty.EOL_STYLE_CRLF)
        val propsExecutable = hashMapOf(SVNProperty.EXECUTABLE to "*")
        val propsSymlink = hashMapOf(SVNProperty.SPECIAL to "*")
        val propsAutoProps = hashMapOf(SVNProperty.INHERITABLE_AUTO_PROPS to "*.txt = svn:eol-style=LF\n")
        val propsNeedsLock = hashMapOf(SVNProperty.NEEDS_LOCK to "*")
    }
}
