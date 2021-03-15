/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.replay

import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.testng.Assert
import org.testng.annotations.Ignore
import org.testng.annotations.Test
import org.tmatesoft.svn.core.*
import org.tmatesoft.svn.core.io.ISVNEditor
import org.tmatesoft.svn.core.io.ISVNReplayHandler
import org.tmatesoft.svn.core.io.ISVNReporter
import org.tmatesoft.svn.core.io.SVNRepository
import svnserver.StringHelper.getFirstLine
import svnserver.SvnConstants
import svnserver.SvnTestHelper
import svnserver.SvnTestServer
import svnserver.TestHelper
import svnserver.repository.VcsConsumer
import svnserver.tester.SvnTesterSvnKit
import java.util.*
import kotlin.math.min

/**
 * Replay svn repository to git repository.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class ReplayTest {
    @Test
    fun testReplayFileModification() {
        SvnTesterSvnKit().use { sourceRepo ->
            SvnTestServer.createEmpty().use { server ->
                val srcRepo = sourceRepo.openSvnRepository()
                val lastCommit = buildHistory(srcRepo)
                val dstRepo: SVNRepository = server.openSvnRepository()
                log.info("Start replay")
                for (revision in 1..lastCommit.newRevision) {
                    val message = srcRepo.getRevisionPropertyValue(revision, "svn:log")
                    log.info("  replay commit #{}: {}", revision, getFirstLine(message.string))
                    replayRangeRevision(srcRepo, dstRepo, revision, false)
                    log.info("  compare revisions #{}: {}", revision, getFirstLine(message.string))
                    compareRevision(srcRepo, revision, dstRepo, dstRepo.latestRevision)
                }
                log.info("End replay")
            }
        }
    }

    private fun buildHistory(repo: SVNRepository): SVNCommitInfo {
        val r1 = createCommit(repo, "Add README.md file") { editor: ISVNEditor ->
            editor.openRoot(0)
            run {
                editor.addFile("/README.md", null, -1)
                editor.changeFileProperty("/README.md", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
                SvnTestHelper.sendDeltaAndClose(editor, "/README.md", null, "aaa")
            }
            editor.closeDir()
        }
        val r2 = createCommit(repo, "Create directory") { editor: ISVNEditor ->
            editor.openRoot(r1.newRevision)
            run {
                editor.addDir("/foo", null, -1)
                run {
                    editor.addDir("/foo/bar", null, -1)
                    run {
                        editor.addFile("/foo/bar/file.txt", null, -1)
                        editor.changeFileProperty("/foo/bar/file.txt", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
                        SvnTestHelper.sendDeltaAndClose(editor, "/foo/bar/file.txt", null, "bbb")
                    }
                    editor.closeDir()
                }
                editor.closeDir()
            }
            editor.closeDir()
        }
        val r3 = createCommit(repo, "Simple files modification") { editor: ISVNEditor ->
            editor.openRoot(r2.newRevision)
            run {
                editor.openFile("/README.md", r2.newRevision)
                SvnTestHelper.sendDeltaAndClose(editor, "/README.md", "aaa", "ccc")
                editor.openFile("/foo/bar/file.txt", -1)
                SvnTestHelper.sendDeltaAndClose(editor, "/foo/bar/file.txt", "bbb", "ddd")
            }
            editor.closeDir()
        }
        val r4 = createCommit(repo, "Simple file copy") { editor: ISVNEditor ->
            editor.openRoot(r3.newRevision)
            run { editor.addFile("/foo/bar/file.copy", "/foo/bar/file.txt", r3.newRevision) }
        }
        val r5 = createCommit(repo, "Simple file move") { editor: ISVNEditor ->
            editor.openRoot(r4.newRevision)
            run {
                editor.addFile("/foo/bar/file.move", "/foo/bar/file.copy", r4.newRevision)
                editor.deleteEntry("/foo/bar/file.copy", r4.newRevision)
            }
        }
        val r6 = createCommit(repo, "Simple directory move") { editor: ISVNEditor ->
            editor.openRoot(r5.newRevision)
            run { editor.addDir("/foo/wow", "/foo/bar", r5.newRevision) }
        }
        val r7 = createCommit(repo, "Directory rename and content change") { editor: ISVNEditor ->
            editor.openRoot(r6.newRevision)
            run {
                editor.addDir("/foo/omg", "/foo/wow", r6.newRevision)
                editor.deleteEntry("/foo/omg/file.move", r6.newRevision)
                editor.openFile("/foo/omg/file.txt", r6.newRevision)
                SvnTestHelper.sendDeltaAndClose(editor, "/foo/omg/file.txt", "ddd", "omg7")
            }
        }
        return createCommit(repo, "Copy and modify directory") { editor: ISVNEditor ->
            editor.openRoot(r7.newRevision)
            run {
                editor.addDir("/foo/omg7", "/foo/wow", r6.newRevision)
                editor.addFile("/foo/omg7/README.md", "/README.md", r1.newRevision)
                editor.openFile("/foo/omg7/file.move", r7.newRevision)
                SvnTestHelper.sendDeltaAndClose(editor, "/foo/omg7/file.move", "ddd", "omg")
                editor.openFile("/foo/omg7/file.txt", r7.newRevision)
                SvnTestHelper.sendDeltaAndClose(editor, "/foo/omg7/file.txt", "ddd", "omg")
            }
        }
    }

    private fun replayRangeRevision(srcRepo: SVNRepository, dstRepo: SVNRepository, revision: Long, checkDelete: Boolean) {
        val copyFroms = TreeMap<Long, CopyFromSVNEditor>()
        srcRepo.replayRange(revision, revision, 0, true, object : ISVNReplayHandler {
            override fun handleStartRevision(revision: Long, revisionProperties: SVNProperties): ISVNEditor {
                val editor = CopyFromSVNEditor(dstRepo.getCommitEditor(revisionProperties.getStringValue("svn:log"), null), "/", checkDelete)
                copyFroms[revision] = editor
                return editor
            }

            override fun handleEndRevision(revision: Long, revisionProperties: SVNProperties, editor: ISVNEditor) {
                editor.closeEdit()
            }
        })
        for ((key, value) in copyFroms) {
            checkCopyFrom(srcRepo, value, key)
        }
    }

    private fun compareRevision(srcRepo: SVNRepository, srcRev: Long, dstRepo: SVNRepository, dstRev: Long) {
        val srcExport = ExportSVNEditor(true)
        srcRepo.diff(srcRepo.location, srcRev, srcRev - 1, null, false, SVNDepth.INFINITY, true, { reporter: ISVNReporter ->
            reporter.setPath("", null, 0, SVNDepth.INFINITY, true)
            reporter.finishReport()
        }, FilterSVNEditor(srcExport, true))
        val dstExport = ExportSVNEditor(true)
        dstRepo.diff(dstRepo.location, dstRev, dstRev - 1, null, false, SVNDepth.INFINITY, true, { reporter: ISVNReporter ->
            reporter.setPath("", null, 0, SVNDepth.INFINITY, true)
            reporter.finishReport()
        }, FilterSVNEditor(dstExport, true))
        Assert.assertEquals(srcExport.toString(), dstExport.toString())
    }

    private fun createCommit(repo: SVNRepository, commitMessage: String, func: VcsConsumer<ISVNEditor>): SVNCommitInfo {
        val editor = repo.getCommitEditor(commitMessage, null)
        return try {
            func.accept(editor)
            val result = editor.closeEdit()
            Assert.assertNotNull(result)
            result
        } finally {
            editor.abortEdit()
        }
    }

    private fun checkCopyFrom(repo: SVNRepository, editor: CopyFromSVNEditor, revision: Long) {
        val copyFrom = TreeMap<String, String>()
        repo.log(arrayOf(""), revision, revision, true, true) { logEntry: SVNLogEntry ->
            for (entry in logEntry.changedPaths.values) {
                if (entry.copyPath != null) {
                    copyFrom[entry.path] = entry.copyPath + "@" + entry.copyRevision
                }
            }
        }
        Assert.assertEquals(editor.getCopyFrom(), copyFrom)
    }

    @Ignore("TODO: issue #237")
    @Test
    fun testPartialReplay() {
        SvnTestServer.createMasterRepository().use { src ->
            SvnTestServer.createEmpty().use { dst ->
                val srcRepo: SVNRepository = SvnTestServer.openSvnRepository(src.url.appendPath("src", false), SvnTestServer.USER_NAME, SvnTestServer.PASSWORD)
                val dstRepo: SVNRepository = dst.openSvnRepository()
                srcRepo.replayRange(
                    1,
                    100,
                    0,
                    true,
                    object : ISVNReplayHandler {
                        override fun handleStartRevision(revision: Long, revisionProperties: SVNProperties): ISVNEditor {
                            return dstRepo.getCommitEditor(revisionProperties.getStringValue(SVNRevisionProperty.LOG), null)
                        }

                        override fun handleEndRevision(revision: Long, revisionProperties: SVNProperties, editor: ISVNEditor) {
                            editor.closeEdit()
                        }
                    }
                )
            }
        }
    }

    @Test
    fun testReplaySelfWithUpdate() {
        checkReplaySelf { srcRepo: SVNRepository, dstRepo: SVNRepository, revision: Long -> updateRevision(srcRepo, dstRepo, revision) }
    }

    private fun checkReplaySelf(replayMethod: ReplayMethod) {
        SvnTestServer.createMasterRepository().use { src ->
            SvnTestServer.createEmpty().use { dst ->
                val srcRepo: SVNRepository = src.openSvnRepository()
                val dstRepo: SVNRepository = dst.openSvnRepository()
                val srcGit: Repository = src.repository
                val dstGit: Repository = dst.repository
                val lastRevision = min(200, srcRepo.latestRevision)
                log.info("Start replay")
                for (revision in 1..lastRevision) {
                    val message = srcRepo.getRevisionPropertyValue(revision, "svn:log")
                    val srcHash = srcRepo.getRevisionPropertyValue(revision, SvnConstants.PROP_GIT)
                    log.info("  replay commit #{} {}: {}", revision, String(srcHash.bytes), getFirstLine(message.string))
                    replayMethod.replay(srcRepo, dstRepo, revision)
                    log.info("  compare revisions #{}: {}", revision, getFirstLine(message.string))
                    compareRevision(srcRepo, revision, dstRepo, revision)
                    val dstHash = dstRepo.getRevisionPropertyValue(revision, SvnConstants.PROP_GIT)
                    compareGitRevision(srcGit, srcHash, dstGit, dstHash)
                }
                log.info("End replay")
            }
        }
    }

    private fun updateRevision(srcRepo: SVNRepository, dstRepo: SVNRepository, revision: Long) {
        val message = srcRepo.getRevisionPropertyValue(revision, "svn:log")
        val editor = CopyFromSVNEditor(dstRepo.getCommitEditor(message.string, null), "/", true)
        srcRepo.update(revision, "", SVNDepth.INFINITY, true, { reporter: ISVNReporter ->
            reporter.setPath("", null, revision - 1, SVNDepth.INFINITY, false)
            reporter.finishReport()
        }, FilterSVNEditor(editor, true))
        checkCopyFrom(srcRepo, editor, revision)
    }

    private fun compareGitRevision(srcGit: Repository, srcHash: SVNPropertyValue, dstGit: Repository, dstHash: SVNPropertyValue) {
        val srcCommit = getCommit(srcGit, srcHash)
        val dstCommit = getCommit(dstGit, dstHash)
        Assert.assertEquals(srcCommit.tree.name, dstCommit.tree.name)
    }

    private fun getCommit(git: Repository, hash: SVNPropertyValue): RevCommit {
        return RevWalk(git).parseCommit(ObjectId.fromString(String(hash.bytes)))
    }

    @Test
    fun testReplaySelfWithReplay() {
        checkReplaySelf { srcRepo: SVNRepository, dstRepo: SVNRepository, revision: Long -> replayRevision(srcRepo, dstRepo, revision) }
    }

    private fun replayRevision(srcRepo: SVNRepository, dstRepo: SVNRepository, revision: Long) {
        val revisionProperties = srcRepo.getRevisionProperties(revision, null)
        val editor = CopyFromSVNEditor(dstRepo.getCommitEditor(revisionProperties.getStringValue("svn:log"), null), "/", true)
        srcRepo.replay(revision - 1, revision, true, editor)
        editor.closeEdit()
        checkCopyFrom(srcRepo, editor, revision)
    }

    @Test
    fun testReplaySelfWithReplayRange() {
        checkReplaySelf { srcRepo: SVNRepository, dstRepo: SVNRepository, revision: Long -> replayRangeRevision(srcRepo, dstRepo, revision, true) }
    }

    private fun interface ReplayMethod {
        fun replay(srcRepo: SVNRepository, dstRepo: SVNRepository, revision: Long)
    }

    companion object {
        private val log = TestHelper.logger
    }
}
