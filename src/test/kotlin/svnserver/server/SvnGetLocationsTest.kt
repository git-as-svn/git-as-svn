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
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.io.ISVNLocationSegmentHandler
import org.tmatesoft.svn.core.io.SVNLocationEntry
import org.tmatesoft.svn.core.io.SVNLocationSegment
import org.tmatesoft.svn.core.io.SVNRepository
import svnserver.SvnTestHelper
import svnserver.SvnTestHelper.modifyFile
import svnserver.SvnTestServer

/**
 * Check file properties.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class SvnGetLocationsTest {
    @Test
    fun segmentsSimple() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            initRepo(repo)
            val last = repo.latestRevision
            checkGetSegments(
                repo, "/baz/test.txt", last, 5, 3,
                "/baz/test.txt@4:5",
                "/bar/test.txt@3:3"
            )
            checkGetSegments(
                repo, "/baz/test.txt", last, 5, 0,
                "/baz/test.txt@4:5",
                "/bar/test.txt@2:3",
                "/foo/test.txt@1:1"
            )
            checkGetSegments(
                repo, "/baz/test.txt", last, 2, 0,
                "/bar/test.txt@2:2",
                "/foo/test.txt@1:1"
            )
            checkGetSegments(
                repo, "/bar/test.txt", 3, 2, 1,
                "/bar/test.txt@2:2",
                "/foo/test.txt@1:1"
            )
            checkGetSegments(
                repo, "/foo/test.txt", 1, 1, 1,
                "/foo/test.txt@1:1"
            )
        }
    }

    private fun initRepo(repo: SVNRepository) {
        // r1 - add single file.
        run {
            val editor = repo.getCommitEditor("Create directory: /foo", null, false, null)
            editor.openRoot(-1)
            editor.addDir("/foo", null, -1)
            // Some file.
            editor.addFile("/foo/test.txt", null, -1)
            SvnTestHelper.sendDeltaAndClose(editor, "/foo/test.txt", null, "Foo content")
            // Close dir
            editor.closeDir()
            editor.closeDir()
            editor.closeEdit()
        }
        // r2 - rename dir
        run {
            val revision = repo.latestRevision
            val editor = repo.getCommitEditor("Rename: /foo to /bar", null, false, null)
            editor.openRoot(-1)
            // Move dir.
            editor.addDir("/bar", "/foo", revision)
            editor.closeDir()
            editor.deleteEntry("/foo", revision)
            // Close dir
            editor.closeDir()
            editor.closeEdit()
        }
        // r3 - modify file.
        modifyFile(repo, "/bar/test.txt", "Bar content", repo.latestRevision)
        // r4 - rename dir
        run {
            val revision = repo.latestRevision
            val editor = repo.getCommitEditor("Rename: /bar to /baz", null, false, null)
            editor.openRoot(-1)
            // Move dir.
            editor.addDir("/baz", "/bar", revision)
            editor.closeDir()
            editor.deleteEntry("/bar", revision)
            // Close dir
            editor.closeDir()
            editor.closeEdit()
        }
        // r5 - modify file.
        modifyFile(repo, "/baz/test.txt", "Baz content", repo.latestRevision)
    }

    private fun checkGetSegments(repo: SVNRepository, path: String, pegRev: Long, startRev: Long, endRev: Long, vararg expected: String) {
        val actual = ArrayList<String>()
        val handler = ISVNLocationSegmentHandler { locationEntry: SVNLocationSegment -> actual.add(locationEntry.path + "@" + locationEntry.startRevision + ":" + locationEntry.endRevision) }
        repo.getLocationSegments(path, pegRev, startRev, endRev, handler)
        Assert.assertEquals(actual.toTypedArray(), expected)
    }

    @Test
    fun segmentsInvalidRange() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            initRepo(repo)
            val last = repo.latestRevision
            try {
                checkGetSegments(repo, "/baz/test.txt", last, 2, 5)
                Assert.fail()
            } catch (e: SVNException) {
                Assert.assertEquals(e.errorMessage.errorCode.code, 204900)
            }
        }
    }

    @Test
    fun segmentsNotFound() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            initRepo(repo)
            val last = repo.latestRevision
            try {
                checkGetSegments(repo, "/baz/test.xml", last, 5, 2)
                Assert.fail()
            } catch (e: SVNException) {
                Assert.assertEquals(e.errorMessage.errorCode, SVNErrorCode.FS_NOT_FOUND)
            }
        }
    }

    @Test
    fun locationsSimple() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            initRepo(repo)
            val last = repo.latestRevision
            checkGetLocations(repo, "/baz/test.txt", last, 5, "/baz/test.txt")
            checkGetLocations(repo, "/baz/test.txt", last, 4, "/baz/test.txt")
            checkGetLocations(repo, "/baz/test.txt", last, 3, "/bar/test.txt")
            checkGetLocations(repo, "/baz/test.txt", last, 2, "/bar/test.txt")
            checkGetLocations(repo, "/baz/test.txt", last, 1, "/foo/test.txt")
            checkGetLocations(repo, "/baz/test.txt", last, 0, null)
            checkGetLocations(repo, "/bar/test.txt", 3, 3, "/bar/test.txt")
            checkGetLocations(repo, "/bar/test.txt", 3, 2, "/bar/test.txt")
            checkGetLocations(repo, "/bar/test.txt", 3, 1, "/foo/test.txt")
            checkGetLocations(repo, "/bar/test.txt", 3, 0, null)
            checkGetLocations(repo, "/bar/test.txt", 2, 2, "/bar/test.txt")
            checkGetLocations(repo, "/bar/test.txt", 2, 1, "/foo/test.txt")
            checkGetLocations(repo, "/bar/test.txt", 2, 0, null)
            checkGetLocations(repo, "/foo/test.txt", 1, 1, "/foo/test.txt")
            checkGetLocations(repo, "/foo/test.txt", 1, 0, null)
        }
    }

    private fun checkGetLocations(repo: SVNRepository, path: String, pegRev: Long, targetRev: Long, expectedPath: String?) {
        val paths = ArrayList<String>()
        repo.getLocations(path, pegRev, longArrayOf(targetRev)) { locationEntry: SVNLocationEntry ->
            Assert.assertEquals(locationEntry.revision, targetRev)
            paths.add(locationEntry.path)
        }
        if (expectedPath == null) {
            Assert.assertTrue(paths.isEmpty())
        } else {
            Assert.assertEquals(paths.size, 1)
            Assert.assertEquals(paths[0], expectedPath)
        }
    }

    @Test
    fun locationsNotFound() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            initRepo(repo)
            try {
                checkGetLocations(repo, "/bar/test.xml", 3, 3, null)
                Assert.fail()
            } catch (e: SVNException) {
                Assert.assertEquals(e.errorMessage.errorCode, SVNErrorCode.FS_NOT_FOUND)
            }
            try {
                checkGetLocations(repo, "/bar/test.txt", 3, 4, null)
                Assert.fail()
            } catch (e: SVNException) {
                Assert.assertEquals(e.errorMessage.errorCode, SVNErrorCode.FS_NOT_FOUND)
            }
        }
    }
}
