/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server

import org.apache.commons.collections4.trie.PatriciaTrie
import org.testng.Assert
import org.testng.annotations.Listeners
import org.testng.annotations.Test
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNLock
import org.tmatesoft.svn.core.io.ISVNLockHandler
import org.tmatesoft.svn.core.io.SVNRepository
import svnserver.StringHelper.normalize
import svnserver.SvnTestHelper
import svnserver.SvnTestHelper.modifyFile
import svnserver.SvnTestServer
import svnserver.tester.SvnTesterDataProvider
import svnserver.tester.SvnTesterExternalListener
import svnserver.tester.SvnTesterFactory

/**
 * Check svn locking.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@Listeners(SvnTesterExternalListener::class)
class SvnLockTest {
    /**
     * Check to take lock on absent file.
     */
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun lockNotExists(factory: SvnTesterFactory) {
        factory.create().use { server ->
            val repo = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/example.txt", "", emptyMap())
            lock(repo, "example2.txt", repo.latestRevision, false, SVNErrorCode.FS_OUT_OF_DATE)
        }
    }

    private fun lock(repo: SVNRepository, path: String, revision: Long, force: Boolean, errorCode: SVNErrorCode?): SVNLock? {
        val pathsToRevisions = PatriciaTrie<Long>()
        pathsToRevisions[path] = revision
        val locks = ArrayList<SVNLock>()
        return try {
            repo.lock(pathsToRevisions, null, force, object : ISVNLockHandler {
                override fun handleLock(path: String, lock: SVNLock?, error: SVNErrorMessage?) {
                    if (error != null) {
                        throw SVNException(error)
                    }
                    Assert.assertNull(errorCode)
                    locks.add(lock!!)
                }

                override fun handleUnlock(path: String, lock: SVNLock?, error: SVNErrorMessage?) {
                    Assert.fail()
                }
            })
            Assert.assertNull(errorCode)
            Assert.assertTrue(locks.size <= 1)
            if (locks.isEmpty()) null else locks[0]
        } catch (e: SVNException) {
            Assert.assertEquals(e.errorMessage.errorCode, errorCode)
            null
        }
    }

    /**
     * Check to take lock of out-of-date file.
     */
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun lockOutOfDate(factory: SvnTesterFactory) {
        factory.create().use { server ->
            val repo = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/example.txt", "", emptyMap())
            val latestRevision = repo.latestRevision
            modifyFile(repo, "/example.txt", "content", latestRevision)
            lock(repo, "example.txt", latestRevision, false, SVNErrorCode.FS_OUT_OF_DATE)
        }
    }

    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun lockNotFile(factory: SvnTesterFactory) {
        factory.create().use { server ->
            val repo = server.openSvnRepository()
            val editor = repo.getCommitEditor("Initial state", null, false, null)
            editor.openRoot(-1)
            editor.addDir("/example", null, -1)
            editor.addFile("/example/example.txt", null, -1)
            SvnTestHelper.sendDeltaAndClose(editor, "/example/example.txt", null, "Source content")
            editor.closeDir()
            editor.closeDir()
            editor.closeEdit()
            val latestRevision = repo.latestRevision
            lock(repo, "example", latestRevision, false, SVNErrorCode.FS_NOT_FILE)
        }
    }

    /**
     * Check to stealing lock.
     */
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun lockForce(factory: SvnTesterFactory) {
        factory.create().use { server ->
            val repo = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/example.txt", "", emptyMap())
            val latestRevision = repo.latestRevision
            val oldLock = lock(repo, "example.txt", latestRevision, false, null)
            Assert.assertNotNull(oldLock)
            compareLock(oldLock, repo.getLock("example.txt"))
            val badLock = lock(repo, "example.txt", latestRevision, false, SVNErrorCode.FS_PATH_ALREADY_LOCKED)
            Assert.assertNull(badLock)
            compareLock(oldLock, repo.getLock("example.txt"))
            val newLock = lock(repo, "example.txt", latestRevision, true, null)
            Assert.assertNotNull(newLock)
            compareLock(newLock, repo.getLock("example.txt"))
        }
    }

    private fun compareLock(actual: SVNLock?, expected: SVNLock?) {
        if (expected == null) {
            Assert.assertNull(actual)
        } else {
            Assert.assertNotNull(actual)
            Assert.assertEquals(actual!!.id, expected.id)
            Assert.assertEquals(actual.comment, expected.comment)
        }
    }

    /**
     * Check to break lock.
     */
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun unlockForce(factory: SvnTesterFactory) {
        factory.create().use { server ->
            val repo = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/example.txt", "", emptyMap())
            val latestRevision = repo.latestRevision
            val oldLock = lock(repo, "example.txt", latestRevision, false, null)
            Assert.assertNotNull(oldLock)
            unlock(repo, oldLock!!, false, null)
            val newLock = lock(repo, "example.txt", latestRevision, true, null)
            Assert.assertNotNull(newLock)
            compareLock(newLock, repo.getLock("example.txt"))
            unlock(repo, oldLock, true, null)
            Assert.assertNull(repo.getLock("example.txt"))
        }
    }

    private fun unlock(repo: SVNRepository, lock: SVNLock, breakLock: Boolean, errorCode: SVNErrorCode?) {
        try {
            val pathsToTokens = PatriciaTrie<String>()
            val root = repo.location.path.substring(repo.getRepositoryRoot(true).path.length)
            Assert.assertTrue(lock.path.startsWith(root))
            pathsToTokens[normalize(lock.path.substring(root.length))] = lock.id
            repo.unlock(pathsToTokens, breakLock, object : ISVNLockHandler {
                override fun handleLock(path: String, lock: SVNLock?, error: SVNErrorMessage?) {
                    Assert.fail()
                }

                override fun handleUnlock(path: String, removedLock: SVNLock, error: SVNErrorMessage?) {
                    if (error != null) {
                        throw SVNException(error)
                    }
                    Assert.assertNull(errorCode)
                    Assert.assertNotNull(removedLock)
                    compareLock(removedLock, lock)
                }
            })
            Assert.assertNull(errorCode)
        } catch (e: SVNException) {
            Assert.assertEquals(e.errorMessage.errorCode, errorCode)
        }
    }

    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun lockSimple(factory: SvnTesterFactory) {
        factory.create().use { server ->
            val repo = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/example.txt", "", emptyMap())
            val latestRevision = repo.latestRevision
            SvnTestHelper.createFile(repo, "/example2.txt", "", emptyMap())
            Assert.assertNull(repo.getLock("example.txt"))

            // New lock
            val lock = lock(repo, "example.txt", latestRevision, false, null)
            Assert.assertNotNull(lock)
            compareLock(repo.getLock("example.txt"), lock)

            // Already locked
            lock(repo, "example.txt", latestRevision, false, SVNErrorCode.FS_PATH_ALREADY_LOCKED)

            // Lock must not changed
            compareLock(repo.getLock("example.txt"), lock)
            unlock(repo, lock!!, false, null)
            Assert.assertNull(repo.getLock("example.txt"))

            // Lock again
            lock(repo, "example.txt", latestRevision, false, null)
        }
    }

    /**
     * Check for deny modify locking file.
     */
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun modifyLocked(factory: SvnTesterFactory) {
        factory.create().use { server ->
            val repo = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/example.txt", "", emptyMap())
            val latestRevision = repo.latestRevision

            // Lock
            val lock = lock(repo, "example.txt", latestRevision, false, null)
            Assert.assertNotNull(lock)
            try {
                modifyFile(repo, "/example.txt", "content", latestRevision)
                Assert.fail()
            } catch (e: SVNException) {
                Assert.assertEquals(e.errorMessage.errorCode, SVNErrorCode.FS_BAD_LOCK_TOKEN)
            }
        }
    }

    /**
     * Check for deny modify locking file.
     */
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun modifyLockedInvalidLock(factory: SvnTesterFactory) {
        factory.create().use { server ->
            val repo = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/example.txt", "", emptyMap())
            val latestRevision = repo.latestRevision

            // Lock
            val oldLock = lock(repo, "example.txt", latestRevision, false, null)
            Assert.assertNotNull(oldLock)
            unlock(repo, oldLock!!, false, null)
            val newLock = lock(repo, "example.txt", latestRevision, false, null)
            val locks = PatriciaTrie<String>()
            locks[oldLock.path] = oldLock.id
            val editor = repo.getCommitEditor("Initial state", locks, false, null)
            try {
                editor.openRoot(-1)
                editor.openFile("/example.txt", latestRevision)
                SvnTestHelper.sendDeltaAndClose(editor, "/example.txt", "", "Source content")
                editor.closeDir()
                editor.closeEdit()
                Assert.fail()
            } catch (e: SVNException) {
                Assert.assertEquals(e.errorMessage.errorCode, SVNErrorCode.FS_BAD_LOCK_TOKEN)
            } finally {
                editor.abortEdit()
            }
            compareLock(server.openSvnRepository().getLock("/example.txt"), newLock)
        }
    }

    /**
     * Check for commit with keep locks.
     */
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun modifyLockedRemoveLock(factory: SvnTesterFactory) {
        factory.create().use { server ->
            val repo = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/example.txt", "", emptyMap())
            val latestRevision = repo.latestRevision

            // Lock
            val lock = lock(repo, "example.txt", latestRevision, false, null)
            Assert.assertNotNull(lock)
            run {
                val locks = PatriciaTrie<String>()
                locks["/example.txt"] = lock!!.id
                val editor = repo.getCommitEditor("Initial state", locks, false, null)
                editor.openRoot(-1)
                editor.openFile("/example.txt", latestRevision)
                SvnTestHelper.sendDeltaAndClose(editor, "/example.txt", "", "Source content")
                editor.closeDir()
                editor.closeEdit()
            }
            Assert.assertNull(repo.getLock("/example.txt"))
        }
    }

    @Test
    fun lockWithDelayedAuth() {
        SvnTestServer.createEmpty(null, true).use { server ->
            val repo: SVNRepository = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/example.txt", "", emptyMap())
            val lock = lock(repo, "/example.txt", repo.latestRevision, false, null)
            Assert.assertNotNull(lock)
        }
    }

    /**
     * Check for commit with remove locks.
     */
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun modifyLockedKeepLock(factory: SvnTesterFactory) {
        factory.create().use { server ->
            val repo = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/example.txt", "", emptyMap())
            val latestRevision = repo.latestRevision

            // Lock
            val lock = lock(repo, "example.txt", latestRevision, false, null)
            Assert.assertNotNull(lock)
            run {
                val locks = PatriciaTrie<String>()
                locks["/example.txt"] = lock!!.id
                val editor = repo.getCommitEditor("Initial state", locks, true, null)
                editor.openRoot(-1)
                editor.openFile("/example.txt", latestRevision)
                SvnTestHelper.sendDeltaAndClose(editor, "/example.txt", "", "Source content")
                editor.closeDir()
                editor.closeEdit()
            }
            compareLock(repo.getLock("/example.txt"), lock)
        }
    }

    /**
     * Check for deny modify locking file.
     */
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun deleteLocked(factory: SvnTesterFactory) {
        factory.create().use { server ->
            val repo = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/example.txt", "", emptyMap())
            val latestRevision = repo.latestRevision

            // Lock
            val lock = lock(repo, "example.txt", latestRevision, false, null)
            Assert.assertNotNull(lock)
            val editor = repo.getCommitEditor("Initial state", null, false, null)
            try {
                editor.openRoot(-1)
                editor.deleteEntry("/example.txt", latestRevision)
                editor.closeDir()
                editor.closeEdit()
                Assert.fail()
            } catch (e: SVNException) {
                Assert.assertEquals(e.errorMessage.errorCode, SVNErrorCode.FS_BAD_LOCK_TOKEN)
            } finally {
                editor.abortEdit()
            }
        }
    }

    /**
     * Check for deny modify locking file.
     */
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun deleteLockedDirNoLock(factory: SvnTesterFactory) {
        factory.create().use { server ->
            val repo = server.openSvnRepository()
            run {
                val editor = repo.getCommitEditor("Initial state", null, false, null)
                editor.openRoot(-1)
                editor.addDir("/example", null, -1)
                editor.addFile("/example/example.txt", null, -1)
                SvnTestHelper.sendDeltaAndClose(editor, "/example/example.txt", null, "Source content")
                editor.closeDir()
                editor.closeDir()
                editor.closeEdit()
            }
            val latestRevision = repo.latestRevision
            // Lock
            val lock = lock(repo, "/example/example.txt", latestRevision, false, null)
            Assert.assertNotNull(lock)
            val editor = repo.getCommitEditor("Initial state", null, false, null)
            try {
                editor.openRoot(-1)
                editor.deleteEntry("/example", latestRevision)
                editor.closeDir()
                editor.closeEdit()
                Assert.fail()
            } catch (e: SVNException) {
                Assert.assertEquals(e.errorMessage.errorCode, SVNErrorCode.FS_BAD_LOCK_TOKEN)
            } finally {
                editor.abortEdit()
            }
        }
    }

    /**
     * Check get locks.
     */
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun getLocks(factory: SvnTesterFactory) {
        factory.create().use { server ->
            val repo = server.openSvnRepository()
            run {
                val editor = repo.getCommitEditor("Initial state", null, false, null)
                editor.openRoot(-1)
                editor.addDir("/example", null, -1)
                editor.addFile("/example/example.txt", null, -1)
                SvnTestHelper.sendDeltaAndClose(editor, "/example/example.txt", null, "Source content")
                editor.closeDir()
                editor.addFile("/foo.txt", null, -1)
                SvnTestHelper.sendDeltaAndClose(editor, "/foo.txt", null, "Source content")
                editor.closeDir()
                editor.closeEdit()
            }
            compareLocks(repo.getLocks(""))
            val latestRevision = repo.latestRevision
            // Lock
            val lock1 = lock(repo, "/example/example.txt", latestRevision, false, null)
            Assert.assertNotNull(lock1)
            val lock2 = lock(repo, "/foo.txt", latestRevision, false, null)
            Assert.assertNotNull(lock2)
            compareLocks(repo.getLocks(""), lock1!!, lock2!!)
        }
    }

    private fun compareLocks(actual: Array<SVNLock>, vararg expected: SVNLock) {
        val actualLocks = PatriciaTrie<SVNLock>()
        for (lock in actual) {
            actualLocks[lock.path] = lock
        }
        for (lock in expected) {
            compareLock(actualLocks.remove(lock.path), lock)
        }
        Assert.assertTrue(actualLocks.isEmpty())
    }

    /**
     * Check for deny modify locking file.
     */
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun deleteLockedDirWithLock(factory: SvnTesterFactory) {
        factory.create().use { server ->
            val repo = server.openSvnRepository()
            run {
                val editor = repo.getCommitEditor("Initial state", null, false, null)
                editor.openRoot(-1)
                editor.addDir("/example", null, -1)
                editor.addFile("/example/example.txt", null, -1)
                SvnTestHelper.sendDeltaAndClose(editor, "/example/example.txt", null, "Source content")
                editor.closeDir()
                editor.closeDir()
                editor.closeEdit()
            }
            val latestRevision = repo.latestRevision
            // Lock
            val lock = lock(repo, "/example/example.txt", latestRevision, false, null)
            Assert.assertNotNull(lock)
            val locks = PatriciaTrie<String>()
            locks[lock!!.path] = lock.id
            val editor = repo.getCommitEditor("Initial state", locks, false, null)
            editor.openRoot(-1)
            editor.deleteEntry("/example", latestRevision)
            editor.closeDir()
            editor.closeEdit()
        }
    }

    /**
     * Try to twice remove lock.
     */
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun unlockTwice(factory: SvnTesterFactory) {
        factory.create().use { server ->
            val repo = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/example.txt", "", emptyMap())
            val latestRevision = repo.latestRevision

            // New lock
            val lock = lock(repo, "example.txt", latestRevision, false, null)
            Assert.assertNotNull(lock)
            unlock(repo, lock!!, false, null)
            unlock(repo, lock, false, SVNErrorCode.FS_NO_SUCH_LOCK)
        }
    }

    /**
     * Try to remove not-owned lock.
     */
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun unlockNotOwner(factory: SvnTesterFactory) {
        factory.create().use { server ->
            val repo = server.openSvnRepository()
            SvnTestHelper.createFile(repo, "/example.txt", "", emptyMap())
            val latestRevision = repo.latestRevision

            // New lock
            val oldLock = lock(repo, "example.txt", latestRevision, false, null)
            Assert.assertNotNull(oldLock)
            unlock(repo, oldLock!!, false, null)
            val newLock = lock(repo, "example.txt", latestRevision, false, null)
            Assert.assertNotNull(newLock)
            unlock(repo, oldLock, false, SVNErrorCode.FS_NO_SUCH_LOCK)
        }
    }
}
