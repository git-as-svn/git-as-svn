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
import org.tmatesoft.svn.core.*
import org.tmatesoft.svn.core.io.SVNRepository
import svnserver.SvnTestHelper
import svnserver.SvnTestHelper.modifyFile
import svnserver.SvnTestServer
import svnserver.ext.gitlfs.storage.local.LfsLocalStorageTest
import svnserver.repository.git.EmptyDirsSupport
import svnserver.repository.git.GitWriter

/**
 * Simple update tests.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class SvnCommitTest {
    @Test
    fun emptyDirDisabled() {
        SvnTestServer.createEmpty(EmptyDirsSupport.Disabled).use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            val editor = repo.getCommitEditor("Initial state", null, false, null)
            editor.openRoot(-1)
            editor.addDir("dir", null, -1)
            editor.closeDir()
            editor.closeDir()
            try {
                editor.closeEdit()
                Assert.fail()
            } catch (e: SVNCancelException) {
                // Expected
            }
        }
    }

    @Test
    fun createEmptyDir() {
        SvnTestServer.createEmpty(EmptyDirsSupport.AutoCreateKeepFile).use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            val editor = repo.getCommitEditor("Initial state", null, false, null)
            editor.openRoot(-1)
            editor.addDir("dir", null, -1)
            editor.closeDir()
            editor.closeDir()
            Assert.assertNotNull(editor.closeEdit())
            SvnTestHelper.checkFileContent(repo, "dir/" + GitWriter.keepFileName, GitWriter.keepFileContents)
        }
    }

    @Test
    fun emptyCommit() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            val editor = repo.getCommitEditor("Initial state", null, false, null)
            editor.openRoot(-1)
            editor.closeDir()
            Assert.assertNotNull(editor.closeEdit())
            Assert.assertEquals(emptyList<Any>(), repo.getDir("", repo.latestRevision, null, 0, ArrayList<SVNDirEntry>()))
        }
    }

    @Test
    fun removeAllFilesFromDir() {
        SvnTestServer.createEmpty(EmptyDirsSupport.AutoCreateKeepFile).use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            val editor = repo.getCommitEditor("Initial state", null, false, null)
            editor.openRoot(-1)
            editor.addDir("dir", null, -1)
            editor.addFile("dir/file", null, 0)
            SvnTestHelper.sendDeltaAndClose(editor, "dir/file", null, "text")
            editor.closeDir()
            editor.closeDir()
            Assert.assertNotNull(editor.closeEdit())
            SvnTestHelper.deleteFile(repo, "dir/file")
            SvnTestHelper.checkFileContent(repo, "dir/" + GitWriter.keepFileName, GitWriter.keepFileContents)
        }
    }

    /**
     * Check file copy.
     * <pre>
     * svn copy README.md@45 README.copy
    </pre> *
     */
    @Test
    fun copyFileFromRevisionTest() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            val srcFile = "/README.md"
            val dstFile = "/README.copy"
            val expectedContent = "New content 2"
            SvnTestHelper.createFile(repo, srcFile, "Old content 1", emptyMap())
            modifyFile(repo, srcFile, expectedContent, repo.latestRevision)
            val srcRev = repo.latestRevision
            modifyFile(repo, srcFile, "New content 3", repo.latestRevision)
            val editor = repo.getCommitEditor("Copy file commit", null, false, null)
            editor.openRoot(-1)
            editor.addFile(dstFile, srcFile, srcRev)
            editor.closeFile(dstFile, null)
            editor.closeDir()
            editor.closeEdit()

            // compare content
            SvnTestHelper.checkFileContent(repo, dstFile, expectedContent)
        }
    }

    @Test
    fun bigFile() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            val data: ByteArray = LfsLocalStorageTest.bigFile()
            SvnTestHelper.createFile(repo, "bla.bin", data, SvnFilePropertyTest.propsBinary)

            // compare content
            SvnTestHelper.checkFileContent(repo, "bla.bin", data)
        }
    }

    /**
     * Check file copy.
     * <pre>
     * svn copy README.md@45 README.copy
    </pre> *
     */
    @Test
    fun copyDirFromRevisionTest() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            run {
                val editor = repo.getCommitEditor("Initial state", null, false, null)
                editor.openRoot(-1)
                editor.addDir("/src", null, -1)
                editor.addDir("/src/main", null, -1)
                editor.addFile("/src/main/source.txt", null, -1)
                SvnTestHelper.sendDeltaAndClose(editor, "/src/main/source.txt", null, "Source content")
                editor.closeDir()
                editor.addDir("/src/test", null, -1)
                editor.addFile("/src/test/test.txt", null, -1)
                SvnTestHelper.sendDeltaAndClose(editor, "/src/test/test.txt", null, "Test content")
                editor.closeDir()
                editor.closeDir()
                editor.closeDir()
                editor.closeEdit()
            }
            SvnTestHelper.createFile(repo, "/src/main/copy-a.txt", "A content", emptyMap())
            val srcDir = "/src/main"
            val dstDir = "/copy"
            val srcRev = repo.latestRevision
            SvnTestHelper.createFile(repo, "/src/main/copy-b.txt", "B content", emptyMap())
            modifyFile(repo, "/src/main/source.txt", "Updated content", repo.latestRevision)
            run {
                val editor = repo.getCommitEditor("Copy dir commit", null, false, null)
                editor.openRoot(-1)
                editor.addDir(dstDir, srcDir, srcRev)
                editor.closeDir()
                editor.closeDir()
                editor.closeEdit()
            }

            // compare content
            val srcList = repo.getDir(srcDir, srcRev, null, 0, ArrayList<SVNDirEntry>())
            val dstList = repo.getDir(dstDir, repo.latestRevision, null, 0, ArrayList<SVNDirEntry>())
            checkEquals(srcList, dstList)
        }
    }

    private fun checkEquals(listA: Collection<SVNDirEntry>, listB: Collection<SVNDirEntry>) {
        val entries = HashSet<String>()
        for (entry in listA) {
            entries.add(entry.name + '\t' + entry.kind + '\t' + entry.size)
        }
        for (entry in listB) {
            Assert.assertTrue(entries.remove(entry.name + '\t' + entry.kind + '\t' + entry.size))
        }
        Assert.assertTrue(entries.isEmpty())
    }

    /**
     * Check commit out-of-date.
     */
    @Test
    fun commitFileOufOfDateTest() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/README.md", "Old content", emptyMap())
            val lastRevision = repo.latestRevision
            modifyFile(repo, "/README.md", "New content 1", lastRevision)
            try {
                modifyFile(repo, "/README.md", "New content 2", lastRevision)
                Assert.fail()
            } catch (e: SVNException) {
                Assert.assertEquals(e.errorMessage.errorCode, SVNErrorCode.WC_NOT_UP_TO_DATE)
            }
        }
    }

    /**
     * Check commit up-to-date.
     */
    @Test
    fun commitFileUpToDateTest() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/README.md", "Old content 1", emptyMap())
            SvnTestHelper.createFile(repo, "/build.gradle", "Old content 2", emptyMap())
            val lastRevision = repo.latestRevision
            modifyFile(repo, "/README.md", "New content 1", lastRevision)
            modifyFile(repo, "/build.gradle", "New content 2", lastRevision)
        }
    }

    /**
     * Check commit without e-mail.
     */
    @Test
    fun commitWithoutEmail() {
        SvnTestServer.createEmpty().use { server ->
            val repo1: SVNRepository = server.openSvnRepository()
            SvnTestHelper.createFile(repo1, "/README.md", "Old content 1", emptyMap())
            SvnTestHelper.createFile(repo1, "/build.gradle", "Old content 2", emptyMap())
            val repo2: SVNRepository = server.openSvnRepository(SvnTestServer.USER_NAME_NO_MAIL, SvnTestServer.PASSWORD)
            val lastRevision = repo2.latestRevision
            SvnTestHelper.checkFileContent(repo2, "/README.md", "Old content 1")
            try {
                modifyFile(repo2, "/README.md", "New content 1", lastRevision)
                Assert.fail("Users with undefined email can't create commits")
            } catch (e: SVNAuthenticationException) {
                Assert.assertTrue(e.message!!.contains("Users with undefined email can't create commits"))
            }
        }
    }
}
