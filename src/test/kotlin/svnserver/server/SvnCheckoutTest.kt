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
import org.tmatesoft.svn.core.wc.*
import org.tmatesoft.svn.core.wc2.SvnOperationFactory
import org.tmatesoft.svn.core.wc2.SvnRevisionRange
import org.tmatesoft.svn.core.wc2.SvnTarget
import svnserver.StringHelper.getFirstLine
import svnserver.SvnTestHelper
import svnserver.SvnTestServer
import svnserver.TestHelper
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.math.min

/**
 * Simple checkout tests.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class SvnCheckoutTest {
    @Test
    @Throws(Exception::class)
    fun checkoutRootRevision() {
        SvnTestServer.createEmpty().use { server ->
            val factory: SvnOperationFactory = server.createOperationFactory()
            val checkout = factory.createCheckout()
            checkout.source = SvnTarget.fromURL(server.url)
            checkout.setSingleTarget(SvnTarget.fromFile(server.tempDirectory.toFile()))
            checkout.revision = SVNRevision.create(0)
            checkout.run()
        }
    }

    /**
     * Workcopy mixed version update smoke test.
     */
    @Test
    @Throws(Exception::class)
    fun randomUpdateRoot() {
        checkUpdate("")
    }

    /**
     * Workcopy mixed version update smoke test.
     */
    @Test
    @Throws(Exception::class)
    fun randomUpdateChild() {
        checkUpdate("/src")
    }

    @Throws(Exception::class)
    private fun checkUpdate(basePath: String) {
        SvnTestServer.createMasterRepository().use { server ->
            val factory: SvnOperationFactory = server.createOperationFactory()
            factory.isAutoCloseContext = false
            factory.setAutoDisposeRepositoryPool(false)
            val repo: SVNRepository = server.openSvnRepository()
            val revisions = loadUpdateRevisions(repo, basePath)
            Assert.assertTrue(revisions.size > 2)
            val checkout = factory.createCheckout()
            checkout.source = SvnTarget.fromURL(server.url.appendPath(basePath, false))
            checkout.setSingleTarget(SvnTarget.fromFile(server.tempDirectory.toFile()))
            checkout.revision = SVNRevision.create(revisions[0])
            checkout.run()
            factory.eventHandler = object : ISVNEventHandler {
                override fun handleEvent(event: SVNEvent, progress: Double) {
                    Assert.assertEquals(event.expectedAction, event.action)
                }

                override fun checkCancelled() {}
            }
            val rand = Random(0)
            for (revision in revisions.subList(1, revisions.size)) {
                val svnLog = factory.createLog()
                svnLog.setSingleTarget(SvnTarget.fromURL(server.url))
                svnLog.revisionRanges = listOf(SvnRevisionRange.create(SVNRevision.create(revision - 1), SVNRevision.create(revision)))
                svnLog.isDiscoverChangedPaths = true
                val logEntry = svnLog.run()
                log.info("Update to revision #{}: {}", revision, getFirstLine(logEntry.message))
                val paths = TreeMap(logEntry.changedPaths)
                val targets = ArrayList<String>()
                val update = factory.createUpdate()
                var lastAdded: String? = null
                for ((path, value) in paths) {
                    if (lastAdded != null && path.startsWith(lastAdded)) {
                        continue
                    }
                    if (value.type == 'A') {
                        lastAdded = "$path/"
                    }
                    if (value.type == 'A' || rand.nextBoolean()) {
                        if (path.startsWith(basePath)) {
                            val subPath = path.substring(basePath.length)
                            targets.add(if (subPath.startsWith("/")) subPath.substring(1) else subPath)
                        }
                    }
                }
                if (targets.isNotEmpty()) {
                    for (target in targets) {
                        update.addTarget(SvnTarget.fromFile(server.tempDirectory.resolve(target).toFile()))
                    }
                    update.revision = SVNRevision.create(revision)
                    update.isSleepForTimestamp = false
                    update.isMakeParents = true
                    update.run()
                }
            }
        }
    }

    @Throws(SVNException::class)
    private fun loadUpdateRevisions(repo: SVNRepository, path: String): List<Long> {
        val maxRevision = min(100, repo.latestRevision)
        val revisions = LinkedList<Long>()
        repo.log(arrayOf(path), maxRevision, 0, false, false) { logEntry: SVNLogEntry -> revisions.addFirst(logEntry.revision) }
        return ArrayList(revisions)
    }

    /**
     * <pre>
     * svn checkout
     * echo > test.txt
     * svn commit -m "create test.txt"
     * rev N
     * echo foo > test.txt
     * svn commit -m "modify test.txt"
     * svn up rev N
    </pre> *
     */
    @Test
    @Throws(Exception::class)
    fun checkoutAndUpdate() {
        SvnTestServer.createEmpty().use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            val editor = repo.getCommitEditor("Initial state", null, false, null)
            editor.openRoot(-1)
            editor.addDir("/src", null, -1)
            editor.addDir("/src/main", null, -1)
            editor.addFile("/src/main/source.txt", null, -1)
            editor.changeFileProperty("/src/main/source.txt", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
            SvnTestHelper.sendDeltaAndClose(editor, "/src/main/source.txt", null, "Source content")
            editor.closeDir()
            editor.addDir("/src/test", null, -1)
            editor.addFile("/src/test/test.txt", null, -1)
            editor.changeFileProperty("/src/test/test.txt", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE))
            SvnTestHelper.sendDeltaAndClose(editor, "/src/test/test.txt", null, "Test content")
            editor.closeDir()
            editor.closeDir()
            editor.closeDir()
            editor.closeEdit()

            // checkout
            val factory: SvnOperationFactory = server.createOperationFactory()
            val checkout = factory.createCheckout()
            checkout.source = SvnTarget.fromURL(server.url)
            checkout.setSingleTarget(SvnTarget.fromFile(server.tempDirectory.toFile()))
            checkout.revision = SVNRevision.HEAD
            checkout.run()
            val file: Path = server.tempDirectory.resolve("src/main/someFile.txt")
            val client = SVNClientManager.newInstance(factory)
            // create file
            val commit: SVNCommitInfo
            run {
                Assert.assertFalse(Files.exists(file))
                TestHelper.saveFile(file, "New content")
                client.wcClient.doAdd(file.toFile(), false, false, false, SVNDepth.INFINITY, false, true)
                client.wcClient.doSetProperty(file.toFile(), SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE), false, SVNDepth.INFINITY, null, null)
                commit = client.commitClient.doCommit(arrayOf(file.toFile()), false, "Commit new file", null, null, false, false, SVNDepth.INFINITY)
            }
            // modify file
            run {
                Assert.assertTrue(Files.exists(file))
                TestHelper.saveFile(file, "Modified content")
                client.commitClient.doCommit(arrayOf(file.toFile()), false, "Modify up-to-date commit", null, null, false, false, SVNDepth.INFINITY)
            }
            // update to previous commit
            client.updateClient.doUpdate(server.tempDirectory.toFile(), SVNRevision.create(commit.newRevision), SVNDepth.INFINITY, false, false)
            // check no tree conflict
            val changeLists = ArrayList<String>()
            client.statusClient.doStatus(server.tempDirectory.toFile(), SVNRevision.WORKING, SVNDepth.INFINITY, false, false, true, false, { status: SVNStatus ->
                Assert.assertNull(status.treeConflict, status.file.toString())
                Assert.assertNull(status.conflictNewFile, status.file.toString())
            }, changeLists)
            Assert.assertTrue(changeLists.isEmpty())
        }
    }

    companion object {
        private val log = TestHelper.logger
    }
}
