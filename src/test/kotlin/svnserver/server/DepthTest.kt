/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server

import org.testng.Assert
import org.testng.annotations.Listeners
import org.testng.annotations.Test
import org.tmatesoft.svn.core.SVNDepth
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNProperty
import org.tmatesoft.svn.core.SVNPropertyValue
import org.tmatesoft.svn.core.io.ISVNReporter
import org.tmatesoft.svn.core.io.ISVNReporterBaton
import svnserver.SvnTestHelper
import svnserver.replay.ReportSVNEditor
import svnserver.tester.SvnTester
import svnserver.tester.SvnTesterDataProvider
import svnserver.tester.SvnTesterExternalListener
import svnserver.tester.SvnTesterFactory

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
@Listeners(SvnTesterExternalListener::class)
class DepthTest {
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    @Throws(Exception::class)
    fun interruptedUpdate(factory: SvnTesterFactory) {
        create(factory).use { server ->
            val revision = server.openSvnRepository().latestRevision
            check(
                server, "", SVNDepth.INFINITY, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.INFINITY, false)
                    reporter.setPath("a/b/c", null, revision, SVNDepth.INFINITY, true)
                    reporter.finishReport()
                }, """ - open-root: r0
/ - change-dir-prop: svn:entry:committed-date
/ - change-dir-prop: svn:entry:committed-rev
/ - change-dir-prop: svn:entry:last-author
/ - change-dir-prop: svn:entry:uuid
a - change-dir-prop: svn:entry:committed-date
a - change-dir-prop: svn:entry:committed-rev
a - change-dir-prop: svn:entry:last-author
a - change-dir-prop: svn:entry:uuid
a - open-dir: r0
a/b - change-dir-prop: svn:entry:committed-date
a/b - change-dir-prop: svn:entry:committed-rev
a/b - change-dir-prop: svn:entry:last-author
a/b - change-dir-prop: svn:entry:uuid
a/b - open-dir: r0
a/b/c - change-dir-prop: svn:entry:committed-date
a/b/c - change-dir-prop: svn:entry:committed-rev
a/b/c - change-dir-prop: svn:entry:last-author
a/b/c - change-dir-prop: svn:entry:uuid
a/b/c - open-dir: r0
a/b/c/d - add-file
a/b/c/d - apply-text-delta: null
a/b/c/d - change-file-prop: svn:entry:committed-date
a/b/c/d - change-file-prop: svn:entry:committed-rev
a/b/c/d - change-file-prop: svn:entry:last-author
a/b/c/d - change-file-prop: svn:entry:uuid
a/b/c/d - change-file-prop: svn:eol-style
a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441
a/b/c/d - delta-chunk
a/b/c/d - delta-end
"""
            )
        }
    }

    @Throws(Exception::class)
    private fun create(factory: SvnTesterFactory): SvnTester {
        val tester = factory.create()
        val repo = tester.openSvnRepository()
        val editor = repo.getCommitEditor("", null)
        editor.openRoot(-1)
        editor.changeDirProperty("svn:ignore", SVNPropertyValue.create("sample.txt"))
        editor.addFile("/.gitattributes", null, -1)
        editor.changeFileProperty("/.gitattributes", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
        SvnTestHelper.sendDeltaAndClose(editor, "/.gitattributes", null, "\n")
        editor.addFile("/.gitignore", null, -1)
        editor.changeFileProperty("/.gitignore", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
        SvnTestHelper.sendDeltaAndClose(editor, "/.gitignore", null, "/sample.txt\n")
        editor.addDir("/a", null, -1)
        editor.addDir("/a/b", null, -1)
        editor.addFile("/a/b/e", null, -1)
        editor.changeFileProperty("/a/b/e", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
        SvnTestHelper.sendDeltaAndClose(editor, "/a/b/e", null, "e body")
        editor.addDir("/a/b/c", null, -1)
        editor.addFile("/a/b/c/d", null, -1)
        editor.changeFileProperty("/a/b/c/d", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
        SvnTestHelper.sendDeltaAndClose(editor, "/a/b/c/d", null, "d body")
        editor.closeDir()
        editor.closeDir()
        editor.closeDir()
        editor.closeDir()
        editor.closeEdit()
        return tester
    }

    @Throws(SVNException::class)
    private fun check(server: SvnTester, path: String, depth: SVNDepth?, reporterBaton: ISVNReporterBaton, expected: String) {
        val repo = server.openSvnRepository()
        val editor = ReportSVNEditor()
        repo.update(repo.latestRevision, path, depth, false, reporterBaton, editor)
        Assert.assertEquals(editor.toString(), expected)
    }

    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    @Throws(Exception::class)
    fun empty(factory: SvnTesterFactory) {
        create(factory).use { server ->
            val revision = server.openSvnRepository().latestRevision
            // svn checkout --depth empty
            check(
                server, "", SVNDepth.EMPTY, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.EMPTY, true)
                    reporter.finishReport()
                }, """ - open-root: r0
/ - change-dir-prop: svn:entry:committed-date
/ - change-dir-prop: svn:entry:committed-rev
/ - change-dir-prop: svn:entry:last-author
/ - change-dir-prop: svn:entry:uuid
/ - change-dir-prop: svn:ignore
"""
            )

            // svn update
            check(
                server, "", SVNDepth.EMPTY, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.EMPTY, false)
                    reporter.finishReport()
                }, """ - open-root: r0
/ - change-dir-prop: svn:entry:committed-date
/ - change-dir-prop: svn:entry:committed-rev
/ - change-dir-prop: svn:entry:last-author
/ - change-dir-prop: svn:entry:uuid
"""
            )

            // svn update --set-depth infinity
            check(
                server, "", SVNDepth.INFINITY, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.EMPTY, false)
                    reporter.finishReport()
                }, """ - open-root: r0
.gitattributes - add-file
.gitattributes - apply-text-delta: null
.gitattributes - change-file-prop: svn:entry:committed-date
.gitattributes - change-file-prop: svn:entry:committed-rev
.gitattributes - change-file-prop: svn:entry:last-author
.gitattributes - change-file-prop: svn:entry:uuid
.gitattributes - change-file-prop: svn:eol-style
.gitattributes - close-file: 68b329da9893e34099c7d8ad5cb9c940
.gitattributes - delta-chunk
.gitattributes - delta-end
.gitignore - add-file
.gitignore - apply-text-delta: null
.gitignore - change-file-prop: svn:entry:committed-date
.gitignore - change-file-prop: svn:entry:committed-rev
.gitignore - change-file-prop: svn:entry:last-author
.gitignore - change-file-prop: svn:entry:uuid
.gitignore - change-file-prop: svn:eol-style
.gitignore - close-file: 57457451fdf67806102d334f30c062f3
.gitignore - delta-chunk
.gitignore - delta-end
/ - change-dir-prop: svn:entry:committed-date
/ - change-dir-prop: svn:entry:committed-rev
/ - change-dir-prop: svn:entry:last-author
/ - change-dir-prop: svn:entry:uuid
a - add-dir
a - change-dir-prop: svn:entry:committed-date
a - change-dir-prop: svn:entry:committed-rev
a - change-dir-prop: svn:entry:last-author
a - change-dir-prop: svn:entry:uuid
a/b - add-dir
a/b - change-dir-prop: svn:entry:committed-date
a/b - change-dir-prop: svn:entry:committed-rev
a/b - change-dir-prop: svn:entry:last-author
a/b - change-dir-prop: svn:entry:uuid
a/b/c - add-dir
a/b/c - change-dir-prop: svn:entry:committed-date
a/b/c - change-dir-prop: svn:entry:committed-rev
a/b/c - change-dir-prop: svn:entry:last-author
a/b/c - change-dir-prop: svn:entry:uuid
a/b/c/d - add-file
a/b/c/d - apply-text-delta: null
a/b/c/d - change-file-prop: svn:entry:committed-date
a/b/c/d - change-file-prop: svn:entry:committed-rev
a/b/c/d - change-file-prop: svn:entry:last-author
a/b/c/d - change-file-prop: svn:entry:uuid
a/b/c/d - change-file-prop: svn:eol-style
a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441
a/b/c/d - delta-chunk
a/b/c/d - delta-end
a/b/e - add-file
a/b/e - apply-text-delta: null
a/b/e - change-file-prop: svn:entry:committed-date
a/b/e - change-file-prop: svn:entry:committed-rev
a/b/e - change-file-prop: svn:entry:last-author
a/b/e - change-file-prop: svn:entry:uuid
a/b/e - change-file-prop: svn:eol-style
a/b/e - close-file: babc2f91dac8ef35815e635d89196696
a/b/e - delta-chunk
a/b/e - delta-end
"""
            )
        }
    }

    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    @Throws(Exception::class)
    fun emptySubdir(factory: SvnTesterFactory) {
        create(factory).use { server ->
            val revision = server.openSvnRepository().latestRevision
            // svn checkout --depth empty
            check(
                server, "a/b", SVNDepth.EMPTY, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.EMPTY, true)
                    reporter.finishReport()
                }, """ - open-root: r0
a/b - change-dir-prop: svn:entry:committed-date
a/b - change-dir-prop: svn:entry:committed-rev
a/b - change-dir-prop: svn:entry:last-author
a/b - change-dir-prop: svn:entry:uuid
a/b - open-dir: r0
"""
            )

            // svn update
            check(server, "a/b", SVNDepth.EMPTY, { reporter: ISVNReporter ->
                reporter.setPath("", null, revision, SVNDepth.EMPTY, false)
                reporter.finishReport()
            }, " - open-root: r0\n")

            // svn update --set-depth infinity
            check(
                server, "a/b", SVNDepth.INFINITY, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.EMPTY, false)
                    reporter.finishReport()
                }, """ - open-root: r0
a/b - change-dir-prop: svn:entry:committed-date
a/b - change-dir-prop: svn:entry:committed-rev
a/b - change-dir-prop: svn:entry:last-author
a/b - change-dir-prop: svn:entry:uuid
a/b - open-dir: r0
a/b/c - add-dir
a/b/c - change-dir-prop: svn:entry:committed-date
a/b/c - change-dir-prop: svn:entry:committed-rev
a/b/c - change-dir-prop: svn:entry:last-author
a/b/c - change-dir-prop: svn:entry:uuid
a/b/c/d - add-file
a/b/c/d - apply-text-delta: null
a/b/c/d - change-file-prop: svn:entry:committed-date
a/b/c/d - change-file-prop: svn:entry:committed-rev
a/b/c/d - change-file-prop: svn:entry:last-author
a/b/c/d - change-file-prop: svn:entry:uuid
a/b/c/d - change-file-prop: svn:eol-style
a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441
a/b/c/d - delta-chunk
a/b/c/d - delta-end
a/b/e - add-file
a/b/e - apply-text-delta: null
a/b/e - change-file-prop: svn:entry:committed-date
a/b/e - change-file-prop: svn:entry:committed-rev
a/b/e - change-file-prop: svn:entry:last-author
a/b/e - change-file-prop: svn:entry:uuid
a/b/e - change-file-prop: svn:eol-style
a/b/e - close-file: babc2f91dac8ef35815e635d89196696
a/b/e - delta-chunk
a/b/e - delta-end
"""
            )
        }
    }

    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    @Throws(Exception::class)
    fun emptySubdir2(factory: SvnTesterFactory) {
        create(factory).use { server ->
            val revision = server.openSvnRepository().latestRevision
            // svn checkout --depth empty
            check(
                server, "a/b/c", SVNDepth.EMPTY, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.EMPTY, true)
                    reporter.finishReport()
                }, """ - open-root: r0
a/b/c - change-dir-prop: svn:entry:committed-date
a/b/c - change-dir-prop: svn:entry:committed-rev
a/b/c - change-dir-prop: svn:entry:last-author
a/b/c - change-dir-prop: svn:entry:uuid
a/b/c - open-dir: r0
"""
            )

            // svn update
            check(server, "a/b/c", SVNDepth.EMPTY, { reporter: ISVNReporter ->
                reporter.setPath("", null, revision, SVNDepth.EMPTY, false)
                reporter.finishReport()
            }, " - open-root: r0\n")

            // svn update --set-depth infinity
            check(
                server, "a/b/c", SVNDepth.INFINITY, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.EMPTY, false)
                    reporter.finishReport()
                }, """ - open-root: r0
a/b/c - change-dir-prop: svn:entry:committed-date
a/b/c - change-dir-prop: svn:entry:committed-rev
a/b/c - change-dir-prop: svn:entry:last-author
a/b/c - change-dir-prop: svn:entry:uuid
a/b/c - open-dir: r0
a/b/c/d - add-file
a/b/c/d - apply-text-delta: null
a/b/c/d - change-file-prop: svn:entry:committed-date
a/b/c/d - change-file-prop: svn:entry:committed-rev
a/b/c/d - change-file-prop: svn:entry:last-author
a/b/c/d - change-file-prop: svn:entry:uuid
a/b/c/d - change-file-prop: svn:eol-style
a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441
a/b/c/d - delta-chunk
a/b/c/d - delta-end
"""
            )
        }
    }

    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    @Throws(Exception::class)
    fun infinity(factory: SvnTesterFactory) {
        create(factory).use { server ->
            val revision = server.openSvnRepository().latestRevision
            // svn checkout --depth infinity a
            check(
                server, "", SVNDepth.INFINITY, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.INFINITY, true)
                    reporter.finishReport()
                }, """ - open-root: r0
.gitattributes - add-file
.gitattributes - apply-text-delta: null
.gitattributes - change-file-prop: svn:entry:committed-date
.gitattributes - change-file-prop: svn:entry:committed-rev
.gitattributes - change-file-prop: svn:entry:last-author
.gitattributes - change-file-prop: svn:entry:uuid
.gitattributes - change-file-prop: svn:eol-style
.gitattributes - close-file: 68b329da9893e34099c7d8ad5cb9c940
.gitattributes - delta-chunk
.gitattributes - delta-end
.gitignore - add-file
.gitignore - apply-text-delta: null
.gitignore - change-file-prop: svn:entry:committed-date
.gitignore - change-file-prop: svn:entry:committed-rev
.gitignore - change-file-prop: svn:entry:last-author
.gitignore - change-file-prop: svn:entry:uuid
.gitignore - change-file-prop: svn:eol-style
.gitignore - close-file: 57457451fdf67806102d334f30c062f3
.gitignore - delta-chunk
.gitignore - delta-end
/ - change-dir-prop: svn:entry:committed-date
/ - change-dir-prop: svn:entry:committed-rev
/ - change-dir-prop: svn:entry:last-author
/ - change-dir-prop: svn:entry:uuid
/ - change-dir-prop: svn:ignore
a - add-dir
a - change-dir-prop: svn:entry:committed-date
a - change-dir-prop: svn:entry:committed-rev
a - change-dir-prop: svn:entry:last-author
a - change-dir-prop: svn:entry:uuid
a/b - add-dir
a/b - change-dir-prop: svn:entry:committed-date
a/b - change-dir-prop: svn:entry:committed-rev
a/b - change-dir-prop: svn:entry:last-author
a/b - change-dir-prop: svn:entry:uuid
a/b/c - add-dir
a/b/c - change-dir-prop: svn:entry:committed-date
a/b/c - change-dir-prop: svn:entry:committed-rev
a/b/c - change-dir-prop: svn:entry:last-author
a/b/c - change-dir-prop: svn:entry:uuid
a/b/c/d - add-file
a/b/c/d - apply-text-delta: null
a/b/c/d - change-file-prop: svn:entry:committed-date
a/b/c/d - change-file-prop: svn:entry:committed-rev
a/b/c/d - change-file-prop: svn:entry:last-author
a/b/c/d - change-file-prop: svn:entry:uuid
a/b/c/d - change-file-prop: svn:eol-style
a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441
a/b/c/d - delta-chunk
a/b/c/d - delta-end
a/b/e - add-file
a/b/e - apply-text-delta: null
a/b/e - change-file-prop: svn:entry:committed-date
a/b/e - change-file-prop: svn:entry:committed-rev
a/b/e - change-file-prop: svn:entry:last-author
a/b/e - change-file-prop: svn:entry:uuid
a/b/e - change-file-prop: svn:eol-style
a/b/e - close-file: babc2f91dac8ef35815e635d89196696
a/b/e - delta-chunk
a/b/e - delta-end
"""
            )

            // svn update
            check(
                server, "", SVNDepth.UNKNOWN, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.INFINITY, false)
                    reporter.finishReport()
                }, """ - open-root: r0
/ - change-dir-prop: svn:entry:committed-date
/ - change-dir-prop: svn:entry:committed-rev
/ - change-dir-prop: svn:entry:last-author
/ - change-dir-prop: svn:entry:uuid
"""
            )
        }
    }

    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    @Throws(Exception::class)
    fun infinitySubdir(factory: SvnTesterFactory) {
        create(factory).use { server ->
            val revision = server.openSvnRepository().latestRevision
            // svn checkout --depth infinity a
            check(
                server, "a", SVNDepth.INFINITY, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.INFINITY, true)
                    reporter.finishReport()
                }, """ - open-root: r0
a - change-dir-prop: svn:entry:committed-date
a - change-dir-prop: svn:entry:committed-rev
a - change-dir-prop: svn:entry:last-author
a - change-dir-prop: svn:entry:uuid
a - open-dir: r0
a/b - add-dir
a/b - change-dir-prop: svn:entry:committed-date
a/b - change-dir-prop: svn:entry:committed-rev
a/b - change-dir-prop: svn:entry:last-author
a/b - change-dir-prop: svn:entry:uuid
a/b/c - add-dir
a/b/c - change-dir-prop: svn:entry:committed-date
a/b/c - change-dir-prop: svn:entry:committed-rev
a/b/c - change-dir-prop: svn:entry:last-author
a/b/c - change-dir-prop: svn:entry:uuid
a/b/c/d - add-file
a/b/c/d - apply-text-delta: null
a/b/c/d - change-file-prop: svn:entry:committed-date
a/b/c/d - change-file-prop: svn:entry:committed-rev
a/b/c/d - change-file-prop: svn:entry:last-author
a/b/c/d - change-file-prop: svn:entry:uuid
a/b/c/d - change-file-prop: svn:eol-style
a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441
a/b/c/d - delta-chunk
a/b/c/d - delta-end
a/b/e - add-file
a/b/e - apply-text-delta: null
a/b/e - change-file-prop: svn:entry:committed-date
a/b/e - change-file-prop: svn:entry:committed-rev
a/b/e - change-file-prop: svn:entry:last-author
a/b/e - change-file-prop: svn:entry:uuid
a/b/e - change-file-prop: svn:eol-style
a/b/e - close-file: babc2f91dac8ef35815e635d89196696
a/b/e - delta-chunk
a/b/e - delta-end
"""
            )

            // svn update
            check(server, "a", SVNDepth.UNKNOWN, { reporter: ISVNReporter ->
                reporter.setPath("", null, revision, SVNDepth.INFINITY, false)
                reporter.finishReport()
            }, " - open-root: r0\n")
        }
    }

    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    @Throws(Exception::class)
    fun files(factory: SvnTesterFactory) {
        create(factory).use { server ->
            val revision = server.openSvnRepository().latestRevision
            // svn checkout --depth infinity a
            check(
                server, "a/b", SVNDepth.FILES, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.INFINITY, true)
                    reporter.finishReport()
                }, """ - open-root: r0
a/b - change-dir-prop: svn:entry:committed-date
a/b - change-dir-prop: svn:entry:committed-rev
a/b - change-dir-prop: svn:entry:last-author
a/b - change-dir-prop: svn:entry:uuid
a/b - open-dir: r0
a/b/e - add-file
a/b/e - apply-text-delta: null
a/b/e - change-file-prop: svn:entry:committed-date
a/b/e - change-file-prop: svn:entry:committed-rev
a/b/e - change-file-prop: svn:entry:last-author
a/b/e - change-file-prop: svn:entry:uuid
a/b/e - change-file-prop: svn:eol-style
a/b/e - close-file: babc2f91dac8ef35815e635d89196696
a/b/e - delta-chunk
a/b/e - delta-end
"""
            )

            // svn update
            check(server, "a/b", SVNDepth.UNKNOWN, { reporter: ISVNReporter ->
                reporter.setPath("", null, revision, SVNDepth.FILES, false)
                reporter.finishReport()
            }, " - open-root: r0\n")

            // svn update --set-depth infinity
            check(
                server, "a/b", SVNDepth.INFINITY, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.FILES, false)
                    reporter.finishReport()
                }, """ - open-root: r0
a/b - change-dir-prop: svn:entry:committed-date
a/b - change-dir-prop: svn:entry:committed-rev
a/b - change-dir-prop: svn:entry:last-author
a/b - change-dir-prop: svn:entry:uuid
a/b - open-dir: r0
a/b/c - add-dir
a/b/c - change-dir-prop: svn:entry:committed-date
a/b/c - change-dir-prop: svn:entry:committed-rev
a/b/c - change-dir-prop: svn:entry:last-author
a/b/c - change-dir-prop: svn:entry:uuid
a/b/c/d - add-file
a/b/c/d - apply-text-delta: null
a/b/c/d - change-file-prop: svn:entry:committed-date
a/b/c/d - change-file-prop: svn:entry:committed-rev
a/b/c/d - change-file-prop: svn:entry:last-author
a/b/c/d - change-file-prop: svn:entry:uuid
a/b/c/d - change-file-prop: svn:eol-style
a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441
a/b/c/d - delta-chunk
a/b/c/d - delta-end
"""
            )
        }
    }

    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    @Throws(Exception::class)
    fun immediates(factory: SvnTesterFactory) {
        create(factory).use { server ->
            val revision = server.openSvnRepository().latestRevision
            // svn checkout --depth immediates a/b
            check(
                server, "a/b", SVNDepth.IMMEDIATES, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.INFINITY, true)
                    reporter.finishReport()
                }, """ - open-root: r0
a/b - change-dir-prop: svn:entry:committed-date
a/b - change-dir-prop: svn:entry:committed-rev
a/b - change-dir-prop: svn:entry:last-author
a/b - change-dir-prop: svn:entry:uuid
a/b - open-dir: r0
a/b/c - add-dir
a/b/c - change-dir-prop: svn:entry:committed-date
a/b/c - change-dir-prop: svn:entry:committed-rev
a/b/c - change-dir-prop: svn:entry:last-author
a/b/c - change-dir-prop: svn:entry:uuid
a/b/e - add-file
a/b/e - apply-text-delta: null
a/b/e - change-file-prop: svn:entry:committed-date
a/b/e - change-file-prop: svn:entry:committed-rev
a/b/e - change-file-prop: svn:entry:last-author
a/b/e - change-file-prop: svn:entry:uuid
a/b/e - change-file-prop: svn:eol-style
a/b/e - close-file: babc2f91dac8ef35815e635d89196696
a/b/e - delta-chunk
a/b/e - delta-end
"""
            )

            // svn update
            check(server, "a/b", SVNDepth.UNKNOWN, { reporter: ISVNReporter ->
                reporter.setPath("", null, revision, SVNDepth.IMMEDIATES, false)
                reporter.finishReport()
            }, " - open-root: r0\n")

            // svn update --set-depth infinity
            check(
                server, "a/b", SVNDepth.INFINITY, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.IMMEDIATES, false)
                    reporter.finishReport()
                }, """ - open-root: r0
a/b - change-dir-prop: svn:entry:committed-date
a/b - change-dir-prop: svn:entry:committed-rev
a/b - change-dir-prop: svn:entry:last-author
a/b - change-dir-prop: svn:entry:uuid
a/b - open-dir: r0
a/b/c - change-dir-prop: svn:entry:committed-date
a/b/c - change-dir-prop: svn:entry:committed-rev
a/b/c - change-dir-prop: svn:entry:last-author
a/b/c - change-dir-prop: svn:entry:uuid
a/b/c - open-dir: r0
a/b/c/d - add-file
a/b/c/d - apply-text-delta: null
a/b/c/d - change-file-prop: svn:entry:committed-date
a/b/c/d - change-file-prop: svn:entry:committed-rev
a/b/c/d - change-file-prop: svn:entry:last-author
a/b/c/d - change-file-prop: svn:entry:uuid
a/b/c/d - change-file-prop: svn:eol-style
a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441
a/b/c/d - delta-chunk
a/b/c/d - delta-end
"""
            )
        }
    }

    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    @Throws(Exception::class)
    fun complex(factory: SvnTesterFactory) {
        create(factory).use { server ->
            val revision = server.openSvnRepository().latestRevision
            // svn checkout --depth infinity
            check(
                server, "", SVNDepth.INFINITY, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.INFINITY, true)
                    reporter.finishReport()
                }, """ - open-root: r0
.gitattributes - add-file
.gitattributes - apply-text-delta: null
.gitattributes - change-file-prop: svn:entry:committed-date
.gitattributes - change-file-prop: svn:entry:committed-rev
.gitattributes - change-file-prop: svn:entry:last-author
.gitattributes - change-file-prop: svn:entry:uuid
.gitattributes - change-file-prop: svn:eol-style
.gitattributes - close-file: 68b329da9893e34099c7d8ad5cb9c940
.gitattributes - delta-chunk
.gitattributes - delta-end
.gitignore - add-file
.gitignore - apply-text-delta: null
.gitignore - change-file-prop: svn:entry:committed-date
.gitignore - change-file-prop: svn:entry:committed-rev
.gitignore - change-file-prop: svn:entry:last-author
.gitignore - change-file-prop: svn:entry:uuid
.gitignore - change-file-prop: svn:eol-style
.gitignore - close-file: 57457451fdf67806102d334f30c062f3
.gitignore - delta-chunk
.gitignore - delta-end
/ - change-dir-prop: svn:entry:committed-date
/ - change-dir-prop: svn:entry:committed-rev
/ - change-dir-prop: svn:entry:last-author
/ - change-dir-prop: svn:entry:uuid
/ - change-dir-prop: svn:ignore
a - add-dir
a - change-dir-prop: svn:entry:committed-date
a - change-dir-prop: svn:entry:committed-rev
a - change-dir-prop: svn:entry:last-author
a - change-dir-prop: svn:entry:uuid
a/b - add-dir
a/b - change-dir-prop: svn:entry:committed-date
a/b - change-dir-prop: svn:entry:committed-rev
a/b - change-dir-prop: svn:entry:last-author
a/b - change-dir-prop: svn:entry:uuid
a/b/c - add-dir
a/b/c - change-dir-prop: svn:entry:committed-date
a/b/c - change-dir-prop: svn:entry:committed-rev
a/b/c - change-dir-prop: svn:entry:last-author
a/b/c - change-dir-prop: svn:entry:uuid
a/b/c/d - add-file
a/b/c/d - apply-text-delta: null
a/b/c/d - change-file-prop: svn:entry:committed-date
a/b/c/d - change-file-prop: svn:entry:committed-rev
a/b/c/d - change-file-prop: svn:entry:last-author
a/b/c/d - change-file-prop: svn:entry:uuid
a/b/c/d - change-file-prop: svn:eol-style
a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441
a/b/c/d - delta-chunk
a/b/c/d - delta-end
a/b/e - add-file
a/b/e - apply-text-delta: null
a/b/e - change-file-prop: svn:entry:committed-date
a/b/e - change-file-prop: svn:entry:committed-rev
a/b/e - change-file-prop: svn:entry:last-author
a/b/e - change-file-prop: svn:entry:uuid
a/b/e - change-file-prop: svn:eol-style
a/b/e - close-file: babc2f91dac8ef35815e635d89196696
a/b/e - delta-chunk
a/b/e - delta-end
"""
            )

            // svn update --set-depth files a/b
            check(server, "a/b", SVNDepth.FILES, { reporter: ISVNReporter ->
                reporter.setPath("", null, revision, SVNDepth.FILES, false)
                reporter.finishReport()
            }, " - open-root: r0\n")

            // svn update --set-depth infinity
            check(
                server, "", SVNDepth.INFINITY, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.INFINITY, false)
                    reporter.setPath("a/b", null, revision, SVNDepth.FILES, false)
                    reporter.finishReport()
                }, """ - open-root: r0
/ - change-dir-prop: svn:entry:committed-date
/ - change-dir-prop: svn:entry:committed-rev
/ - change-dir-prop: svn:entry:last-author
/ - change-dir-prop: svn:entry:uuid
a - change-dir-prop: svn:entry:committed-date
a - change-dir-prop: svn:entry:committed-rev
a - change-dir-prop: svn:entry:last-author
a - change-dir-prop: svn:entry:uuid
a - open-dir: r0
a/b - change-dir-prop: svn:entry:committed-date
a/b - change-dir-prop: svn:entry:committed-rev
a/b - change-dir-prop: svn:entry:last-author
a/b - change-dir-prop: svn:entry:uuid
a/b - open-dir: r0
a/b/c - add-dir
a/b/c - change-dir-prop: svn:entry:committed-date
a/b/c - change-dir-prop: svn:entry:committed-rev
a/b/c - change-dir-prop: svn:entry:last-author
a/b/c - change-dir-prop: svn:entry:uuid
a/b/c/d - add-file
a/b/c/d - apply-text-delta: null
a/b/c/d - change-file-prop: svn:entry:committed-date
a/b/c/d - change-file-prop: svn:entry:committed-rev
a/b/c/d - change-file-prop: svn:entry:last-author
a/b/c/d - change-file-prop: svn:entry:uuid
a/b/c/d - change-file-prop: svn:eol-style
a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441
a/b/c/d - delta-chunk
a/b/c/d - delta-end
"""
            )

            // svn update --set-depth empty a/b
            check(server, "a/b", SVNDepth.EMPTY, { reporter: ISVNReporter ->
                reporter.setPath("", null, revision, SVNDepth.EMPTY, false)
                reporter.finishReport()
            }, " - open-root: r0\n")

            // svn update --set-depth infinity
            check(
                server, "", SVNDepth.INFINITY, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.INFINITY, false)
                    reporter.setPath("a/b", null, revision, SVNDepth.EMPTY, false)
                    reporter.finishReport()
                }, """ - open-root: r0
/ - change-dir-prop: svn:entry:committed-date
/ - change-dir-prop: svn:entry:committed-rev
/ - change-dir-prop: svn:entry:last-author
/ - change-dir-prop: svn:entry:uuid
a - change-dir-prop: svn:entry:committed-date
a - change-dir-prop: svn:entry:committed-rev
a - change-dir-prop: svn:entry:last-author
a - change-dir-prop: svn:entry:uuid
a - open-dir: r0
a/b - change-dir-prop: svn:entry:committed-date
a/b - change-dir-prop: svn:entry:committed-rev
a/b - change-dir-prop: svn:entry:last-author
a/b - change-dir-prop: svn:entry:uuid
a/b - open-dir: r0
a/b/c - add-dir
a/b/c - change-dir-prop: svn:entry:committed-date
a/b/c - change-dir-prop: svn:entry:committed-rev
a/b/c - change-dir-prop: svn:entry:last-author
a/b/c - change-dir-prop: svn:entry:uuid
a/b/c/d - add-file
a/b/c/d - apply-text-delta: null
a/b/c/d - change-file-prop: svn:entry:committed-date
a/b/c/d - change-file-prop: svn:entry:committed-rev
a/b/c/d - change-file-prop: svn:entry:last-author
a/b/c/d - change-file-prop: svn:entry:uuid
a/b/c/d - change-file-prop: svn:eol-style
a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441
a/b/c/d - delta-chunk
a/b/c/d - delta-end
a/b/e - add-file
a/b/e - apply-text-delta: null
a/b/e - change-file-prop: svn:entry:committed-date
a/b/e - change-file-prop: svn:entry:committed-rev
a/b/e - change-file-prop: svn:entry:last-author
a/b/e - change-file-prop: svn:entry:uuid
a/b/e - change-file-prop: svn:eol-style
a/b/e - close-file: babc2f91dac8ef35815e635d89196696
a/b/e - delta-chunk
a/b/e - delta-end
"""
            )

            // svn update --set-depth immediates a/b
            check(server, "a/b", SVNDepth.IMMEDIATES, { reporter: ISVNReporter ->
                reporter.setPath("", null, revision, SVNDepth.IMMEDIATES, false)
                reporter.finishReport()
            }, " - open-root: r0\n")

            // svn update --set-depth infinity
            check(
                server, "", SVNDepth.INFINITY, { reporter: ISVNReporter ->
                    reporter.setPath("", null, revision, SVNDepth.INFINITY, false)
                    reporter.setPath("a/b", null, revision, SVNDepth.IMMEDIATES, false)
                    reporter.setPath("a/b/c", null, revision, SVNDepth.EMPTY, false)
                    reporter.finishReport()
                }, """ - open-root: r0
/ - change-dir-prop: svn:entry:committed-date
/ - change-dir-prop: svn:entry:committed-rev
/ - change-dir-prop: svn:entry:last-author
/ - change-dir-prop: svn:entry:uuid
a - change-dir-prop: svn:entry:committed-date
a - change-dir-prop: svn:entry:committed-rev
a - change-dir-prop: svn:entry:last-author
a - change-dir-prop: svn:entry:uuid
a - open-dir: r0
a/b - change-dir-prop: svn:entry:committed-date
a/b - change-dir-prop: svn:entry:committed-rev
a/b - change-dir-prop: svn:entry:last-author
a/b - change-dir-prop: svn:entry:uuid
a/b - open-dir: r0
a/b/c - change-dir-prop: svn:entry:committed-date
a/b/c - change-dir-prop: svn:entry:committed-rev
a/b/c - change-dir-prop: svn:entry:last-author
a/b/c - change-dir-prop: svn:entry:uuid
a/b/c - open-dir: r0
a/b/c/d - add-file
a/b/c/d - apply-text-delta: null
a/b/c/d - change-file-prop: svn:entry:committed-date
a/b/c/d - change-file-prop: svn:entry:committed-rev
a/b/c/d - change-file-prop: svn:entry:last-author
a/b/c/d - change-file-prop: svn:entry:uuid
a/b/c/d - change-file-prop: svn:eol-style
a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441
a/b/c/d - delta-chunk
a/b/c/d - delta-end
"""
            )
        }
    }
}
