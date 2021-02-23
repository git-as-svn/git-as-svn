/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server

import org.testng.annotations.Test
import org.testng.internal.junit.ArrayAsserts
import org.tmatesoft.svn.core.*
import org.tmatesoft.svn.core.io.SVNRepository
import svnserver.SvnTestHelper
import svnserver.SvnTestHelper.modifyFile
import svnserver.SvnTestServer
import java.util.*

/**
 * Check file properties.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class SvnLogTest {
    /**
     * Check simple svn log behaviour.
     */
    @Test
    @Throws(Exception::class)
    fun simple() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            // r1 - add single file.
            SvnTestHelper.createFile(repo, "/foo.txt", "", SvnFilePropertyTest.propsEolNative)
            // r2 - add file in directory.
            run {
                val latestRevision = repo.latestRevision
                val editor = repo.getCommitEditor("Create directory: /foo", null, false, null)
                editor.openRoot(latestRevision)
                editor.addDir("/foo", null, -1)
                editor.addFile("/foo/bar.txt", null, -1)
                editor.changeFileProperty("/foo/bar.txt", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
                SvnTestHelper.sendDeltaAndClose(editor, "/foo/bar.txt", null, "File body")
                editor.closeDir()
                editor.closeDir()
                editor.closeEdit()
            }
            // r3 - change file in directory.
            modifyFile(repo, "/foo/bar.txt", "New body", repo.latestRevision)
            // r4 - change file in directory.
            SvnTestHelper.createFile(repo, "/foo/foo.txt", "New body", SvnFilePropertyTest.propsEolNative)

            // svn log from root
            val last = repo.latestRevision
            checkLog(
                repo, last, 0, "/",
                LogEntry(4, "Create file: /foo/foo.txt", "A /foo/foo.txt"),
                LogEntry(3, "Modify file: /foo/bar.txt", "M /foo/bar.txt"),
                LogEntry(2, "Create directory: /foo", "A /foo", "A /foo/bar.txt"),
                LogEntry(1, "Create file: /foo.txt", "A /foo.txt"),
                LogEntry(0, null)
            )

            // svn log from root
            checkLog(
                repo, last, 0, "/foo",
                LogEntry(4, "Create file: /foo/foo.txt", "A /foo/foo.txt"),
                LogEntry(3, "Modify file: /foo/bar.txt", "M /foo/bar.txt"),
                LogEntry(2, "Create directory: /foo", "A /foo", "A /foo/bar.txt")
            )

            // svn log from root
            checkLog(
                repo, last, 0, "/foo/bar.txt",
                LogEntry(3, "Modify file: /foo/bar.txt", "M /foo/bar.txt"),
                LogEntry(2, "Create directory: /foo", "A /foo", "A /foo/bar.txt")
            )

            // svn empty log
            checkLog(
                repo, 0, 0, "/",
                LogEntry(0, null)
            )
        }
    }

    @Throws(SVNException::class)
    private fun checkLog(repo: SVNRepository, r1: Long, r2: Long, path: String, vararg expecteds: LogEntry) {
        checkLogLimit(repo, r1, r2, 0, path, *expecteds)
    }

    @Throws(SVNException::class)
    private fun checkLogLimit(repo: SVNRepository, r1: Long, r2: Long, limit: Int, path: String, vararg expecteds: LogEntry) {
        val actual: MutableList<LogEntry> = ArrayList()
        repo.log(arrayOf(path), r1, r2, true, false, limit.toLong()) { logEntry: SVNLogEntry -> actual.add(LogEntry(logEntry)) }
        ArrayAsserts.assertArrayEquals(expecteds, actual.toTypedArray())
    }

    /**
     * Check file recreate log test.
     */
    @Test
    @Throws(Exception::class)
    fun recreateFile() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            // r1 - add single file.
            SvnTestHelper.createFile(repo, "/foo.txt", "", SvnFilePropertyTest.propsEolNative)
            // r2 - modify file.
            modifyFile(repo, "/foo.txt", "New content", repo.latestRevision)
            // r3 - remove file.
            SvnTestHelper.deleteFile(repo, "/foo.txt")
            val delete = repo.latestRevision
            // r4 - recreate file.
            SvnTestHelper.createFile(repo, "/foo.txt", "", SvnFilePropertyTest.propsEolNative)

            // svn log from root
            val last = repo.latestRevision
            checkLog(
                repo, last, 0, "/foo.txt",
                LogEntry(4, "Create file: /foo.txt", "A /foo.txt")
            )

            // svn log from root
            checkLog(
                repo, delete - 1, 0, "/foo.txt",
                LogEntry(2, "Modify file: /foo.txt", "M /foo.txt"),
                LogEntry(1, "Create file: /foo.txt", "A /foo.txt")
            )
        }
    }

    /**
     * Check file recreate log test.
     */
    @Test
    @Throws(Exception::class)
    fun recreateDirectory() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            // r1 - add single file.
            run {
                val editor = repo.getCommitEditor("Create directory: /foo", null, false, null)
                editor.openRoot(-1)
                editor.addDir("/foo", null, -1)
                // Empty file.
                val file = "/foo/bar.txt"
                editor.addFile(file, null, -1)
                editor.changeFileProperty(file, SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
                SvnTestHelper.sendDeltaAndClose(editor, file, null, "")
                // Close dir
                editor.closeDir()
                editor.closeDir()
                editor.closeEdit()
            }
            // r2 - modify file.
            modifyFile(repo, "/foo/bar.txt", "New content", repo.latestRevision)
            // r3 - remove directory.
            SvnTestHelper.deleteFile(repo, "/foo")
            val delete = repo.latestRevision
            // r4 - recreate file.
            run {
                val editor = repo.getCommitEditor("Create directory: /foo", null, false, null)
                editor.openRoot(-1)
                editor.addDir("/foo", null, -1)
                // Empty file.
                val file = "/foo/bar.txt"
                editor.addFile(file, null, -1)
                editor.changeFileProperty(file, SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
                SvnTestHelper.sendDeltaAndClose(editor, file, null, "")
                // Close dir
                editor.closeDir()
                editor.closeDir()
                editor.closeEdit()
            }

            // svn log from latest revision
            val last = repo.latestRevision
            checkLog(
                repo, last, 0, "/foo/bar.txt",
                LogEntry(4, "Create directory: /foo", "A /foo", "A /foo/bar.txt")
            )

            // svn log from revision before delete
            checkLog(
                repo, delete - 1, 0, "/foo/bar.txt",
                LogEntry(2, "Modify file: /foo/bar.txt", "M /foo/bar.txt"),
                LogEntry(1, "Create directory: /foo", "A /foo", "A /foo/bar.txt")
            )
        }
    }

    /**
     * Check file move log test.
     */
    @Test
    @Throws(Exception::class)
    fun moveFile() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            // r1 - add single file.
            SvnTestHelper.createFile(repo, "/foo.txt", "Foo content", SvnFilePropertyTest.propsEolNative)
            // r2 - rename file
            run {
                val revision = repo.latestRevision
                val editor = repo.getCommitEditor("Rename: /foo.txt to /bar.txt", null, false, null)
                editor.openRoot(-1)
                // Empty file.
                editor.addFile("/bar.txt", "/foo.txt", revision)
                editor.changeFileProperty("/bar.txt", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
                editor.closeFile("/bar.txt", null)
                editor.deleteEntry("/foo.txt", revision)
                // Close dir
                editor.closeDir()
                editor.closeEdit()
            }
            // r3 - modify file.
            modifyFile(repo, "/bar.txt", "Bar content", repo.latestRevision)
            // r4 - rename file
            run {
                val revision = repo.latestRevision
                val editor = repo.getCommitEditor("Rename: /bar.txt to /baz.txt", null, false, null)
                editor.openRoot(-1)
                // Empty file.
                editor.addFile("/baz.txt", "/bar.txt", revision)
                editor.changeFileProperty("/baz.txt", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
                editor.closeFile("/baz.txt", null)
                editor.deleteEntry("/bar.txt", revision)
                // Close dir
                editor.closeDir()
                editor.closeEdit()
            }
            // r5 - modify file.
            modifyFile(repo, "/baz.txt", "Baz content", repo.latestRevision)
            val last = repo.latestRevision
            // r6 - remove file.
            SvnTestHelper.deleteFile(repo, "/baz.txt")

            // svn log from last file exists revision
            checkLog(
                repo, last, 0, "/baz.txt",
                LogEntry(5, "Modify file: /baz.txt", "M /baz.txt"),
                LogEntry(4, "Rename: /bar.txt to /baz.txt", "D /bar.txt", "A /baz.txt"),
                LogEntry(3, "Modify file: /bar.txt", "M /bar.txt"),
                LogEntry(2, "Rename: /foo.txt to /bar.txt", "D /foo.txt", "A /bar.txt"),
                LogEntry(1, "Create file: /foo.txt", "A /foo.txt")
            )

            // svn log from last file exists revision
            checkLog(
                repo, 0, last, "/baz.txt",
                LogEntry(1, "Create file: /foo.txt", "A /foo.txt"),
                LogEntry(2, "Rename: /foo.txt to /bar.txt", "D /foo.txt", "A /bar.txt"),
                LogEntry(3, "Modify file: /bar.txt", "M /bar.txt"),
                LogEntry(4, "Rename: /bar.txt to /baz.txt", "D /bar.txt", "A /baz.txt"),
                LogEntry(5, "Modify file: /baz.txt", "M /baz.txt")
            )

            // svn log from last file exists revision
            checkLogLimit(
                repo, last, 0, 3, "/baz.txt",
                LogEntry(5, "Modify file: /baz.txt", "M /baz.txt"),
                LogEntry(4, "Rename: /bar.txt to /baz.txt", "D /bar.txt", "A /baz.txt"),
                LogEntry(3, "Modify file: /bar.txt", "M /bar.txt")
            )

            // svn log from last file exists revision
            checkLogLimit(
                repo, 0, last, 3, "/baz.txt",
                LogEntry(1, "Create file: /foo.txt", "A /foo.txt"),
                LogEntry(2, "Rename: /foo.txt to /bar.txt", "D /foo.txt", "A /bar.txt"),
                LogEntry(3, "Modify file: /bar.txt", "M /bar.txt")
            )

            // svn log from last file exists revision
            checkLog(
                repo, 3, 0, "/bar.txt",
                LogEntry(3, "Modify file: /bar.txt", "M /bar.txt"),
                LogEntry(2, "Rename: /foo.txt to /bar.txt", "D /foo.txt", "A /bar.txt"),
                LogEntry(1, "Create file: /foo.txt", "A /foo.txt")
            )
        }
    }

    /**
     * Check file move log test.
     */
    @Test
    @Throws(Exception::class)
    fun moveDirectory() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            // r1 - add single file.
            run {
                val editor = repo.getCommitEditor("Create directory: /foo", null, false, null)
                editor.openRoot(-1)
                editor.addDir("/foo", null, -1)
                // Some file.
                editor.addFile("/foo/test.txt", null, -1)
                editor.changeFileProperty("/foo/test.txt", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
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
            val last = repo.latestRevision

            // svn log from last file exists revision
            checkLog(
                repo, last, 0, "/baz/test.txt",
                LogEntry(5, "Modify file: /baz/test.txt", "M /baz/test.txt"),
                LogEntry(4, "Rename: /bar to /baz", "D /bar", "A /baz", "A /baz/test.txt"),
                LogEntry(3, "Modify file: /bar/test.txt", "M /bar/test.txt"),
                LogEntry(2, "Rename: /foo to /bar", "D /foo", "A /bar", "A /bar/test.txt"),
                LogEntry(1, "Create directory: /foo", "A /foo", "A /foo/test.txt")
            )

            // svn log from last file exists revision
            checkLog(
                repo, 0, last, "/baz/test.txt",
                LogEntry(1, "Create directory: /foo", "A /foo", "A /foo/test.txt"),
                LogEntry(2, "Rename: /foo to /bar", "D /foo", "A /bar", "A /bar/test.txt"),
                LogEntry(3, "Modify file: /bar/test.txt", "M /bar/test.txt"),
                LogEntry(4, "Rename: /bar to /baz", "D /bar", "A /baz", "A /baz/test.txt"),
                LogEntry(5, "Modify file: /baz/test.txt", "M /baz/test.txt")
            )

            // svn log from last file exists revision
            checkLogLimit(
                repo, last, 0, 3, "/baz/test.txt",
                LogEntry(5, "Modify file: /baz/test.txt", "M /baz/test.txt"),
                LogEntry(4, "Rename: /bar to /baz", "D /bar", "A /baz", "A /baz/test.txt"),
                LogEntry(3, "Modify file: /bar/test.txt", "M /bar/test.txt")
            )

            // svn log from last file exists revision
            checkLogLimit(
                repo, 0, last, 3, "/baz/test.txt",
                LogEntry(1, "Create directory: /foo", "A /foo", "A /foo/test.txt"),
                LogEntry(2, "Rename: /foo to /bar", "D /foo", "A /bar", "A /bar/test.txt"),
                LogEntry(3, "Modify file: /bar/test.txt", "M /bar/test.txt")
            )
        }
    }

    private class LogEntry private constructor(private val revision: Long, private val message: String?, paths: Collection<String>) {
        private val paths: Set<String>

        constructor(logEntry: SVNLogEntry) : this(logEntry.revision, logEntry.message, convert(logEntry.changedPaths.values))
        constructor(revision: Long, message: String?, vararg paths: String) : this(revision, message, paths.toList())

        override fun hashCode(): Int {
            var result = (revision xor (revision ushr 32)).toInt()
            if (message != null) result = 31 * result + message.hashCode()
            result = 31 * result + paths.hashCode()
            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val logEntry = other as LogEntry
            return (revision == logEntry.revision && message == logEntry.message
                    && paths == logEntry.paths)
        }

        override fun toString(): String {
            return "LogEntry{" +
                    "revision=" + revision +
                    ", message='" + message + '\'' +
                    ", paths=" + paths +
                    '}'
        }

        companion object {
            private fun convert(changedPaths: Collection<SVNLogEntryPath>): Collection<String> {
                val result: MutableList<String> = ArrayList()
                for (logPath in changedPaths) {
                    result.add(logPath.type.toString() + " " + logPath.path)
                }
                return result
            }
        }

        init {
            this.paths = TreeSet(paths)
        }
    }
}
