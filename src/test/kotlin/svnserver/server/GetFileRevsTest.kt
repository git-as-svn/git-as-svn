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
import org.tmatesoft.svn.core.io.SVNCapability
import org.tmatesoft.svn.core.io.SVNFileRevision
import org.tmatesoft.svn.core.io.SVNRepository
import svnserver.SvnTestHelper
import svnserver.SvnTestHelper.modifyFile
import svnserver.tester.SvnTesterDataProvider
import svnserver.tester.SvnTesterExternalListener
import svnserver.tester.SvnTesterFactory

@Listeners(SvnTesterExternalListener::class)
class GetFileRevsTest {
    @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider::class)
    fun simple(factory: SvnTesterFactory) {
        factory.create().use { tester ->
            val repository = tester.openSvnRepository()
            SvnTestHelper.createFile(repository, fileName, "a\nb\nc\n", emptyMap())
            modifyFile(repository, fileName, "a\nd\nc\n", repository.latestRevision)
            val latestRevision = repository.latestRevision
            try {
                assertFileRevisions(repository, 0, 0)
                Assert.fail()
            } catch (e: SVNException) {
                Assert.assertEquals(e.errorMessage.errorCode, SVNErrorCode.FS_NOT_FILE)
            }
            if (hasCapability(repository, SVNCapability.GET_FILE_REVS_REVERSED)) assertFileRevisions(repository, -1, 0, latestRevision, latestRevision - 1)
            assertFileRevisions(repository, -1, -1, latestRevision)
            assertFileRevisions(repository, 0, -1, latestRevision - 1, latestRevision)
            assertFileRevisions(repository, latestRevision - 1, latestRevision - 1, latestRevision - 1)
        }
    }

    private fun assertFileRevisions(repository: SVNRepository, startRev: Long, endRev: Long, vararg expected: Long) {
        val fileRevisions: List<SVNFileRevision> = ArrayList()
        repository.getFileRevisions(fileName, fileRevisions, startRev, endRev)
        Assert.assertEquals(fileRevisions.size, expected.size)
        for (i in expected.indices) {
            Assert.assertEquals(fileRevisions[i].revision, expected[i])
        }
    }

    companion object {
        private const val fileName = "/file.txt"
        private fun hasCapability(repository: SVNRepository, capability: SVNCapability): Boolean {
            try {
                return repository.hasCapability(capability)
            } catch (e: SVNException) {
                if (e.errorMessage.errorCode !== SVNErrorCode.UNKNOWN_CAPABILITY) throw e
            }
            return false
        }
    }
}
