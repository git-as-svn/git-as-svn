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
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNNodeKind
import org.tmatesoft.svn.core.io.SVNRepository
import svnserver.SvnTestHelper
import svnserver.tester.SvnTester
import svnserver.tester.SvnTesterDataProvider
import svnserver.tester.SvnTesterExternalListener
import svnserver.tester.SvnTesterFactory

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
@Listeners(SvnTesterExternalListener::class)
class CheckPathAndStatCmdTest {
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun nonexistentRev(factory: SvnTesterFactory) {
        create(factory).use { tester ->
            val repository = tester.openSvnRepository()
            val revision = repository.latestRevision + 1
            try {
                repository.checkPath("", revision)
                Assert.fail()
            } catch (e: SVNException) {
                Assert.assertEquals(e.errorMessage.errorCode, SVNErrorCode.FS_NO_SUCH_REVISION)
            }
            try {
                repository.info("", revision)
                Assert.fail()
            } catch (e: SVNException) {
                Assert.assertEquals(e.errorMessage.errorCode, SVNErrorCode.FS_NO_SUCH_REVISION)
            }
        }
    }

    private fun create(factory: SvnTesterFactory): SvnTester {
        val tester = factory.create()
        SvnTestHelper.createFile(tester.openSvnRepository(), "/existent", "", emptyMap())
        return tester
    }

    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun nonexistentFileInInitialRev(factory: SvnTesterFactory) {
        create(factory).use { tester ->
            val repository = tester.openSvnRepository()
            assertPath(repository, "/existent", 0, SVNNodeKind.NONE)
        }
    }

    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun nonexistentFile(factory: SvnTesterFactory) {
        create(factory).use { tester ->
            val repository = tester.openSvnRepository()
            assertPath(repository, "/nonexistent", repository.latestRevision, SVNNodeKind.NONE)
        }
    }

    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun root(factory: SvnTesterFactory) {
        create(factory).use { tester ->
            val repository = tester.openSvnRepository()
            assertPath(repository, "", repository.latestRevision, SVNNodeKind.DIR)
        }
    }

    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun existentFile(factory: SvnTesterFactory) {
        create(factory).use { tester ->
            val repository = tester.openSvnRepository()
            assertPath(repository, "/existent", repository.latestRevision, SVNNodeKind.FILE)
        }
    }

    companion object {
        private fun assertPath(repository: SVNRepository, path: String, rev: Long, expectedKind: SVNNodeKind) {
            val nodeKind = repository.checkPath(path, rev)
            Assert.assertEquals(nodeKind, expectedKind)
            val info = repository.info(path, rev)
            if (expectedKind == SVNNodeKind.NONE) {
                Assert.assertNull(info)
            } else {
                Assert.assertEquals(info.kind, expectedKind)
                Assert.assertEquals(info.revision, rev)
            }
        }
    }
}
