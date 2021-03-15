/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.local

import com.google.common.hash.Hashing
import com.google.common.io.CharStreams
import org.apache.commons.io.IOUtils
import org.eclipse.jgit.util.Holder
import org.testng.Assert
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNLock
import org.tmatesoft.svn.core.io.ISVNLockHandler
import org.tmatesoft.svn.core.io.SVNRepository
import ru.bozaro.gitlfs.client.exceptions.RequestException
import ru.bozaro.gitlfs.common.LockConflictException
import svnserver.SvnTestHelper
import svnserver.SvnTestServer
import svnserver.TemporaryOutputStream
import svnserver.TestHelper
import svnserver.auth.User
import svnserver.ext.gitlfs.LocalLfsConfig
import svnserver.ext.gitlfs.storage.LfsStorage
import svnserver.repository.git.GitRepository
import svnserver.repository.locks.LockDesc
import svnserver.repository.locks.LockTarget
import svnserver.repository.locks.UnlockTarget
import svnserver.server.SvnFilePropertyTest
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentSkipListMap
import javax.servlet.http.HttpServletResponse

/**
 * Simple test for LfsLocalStorage.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class LfsLocalStorageTest {
    @Test
    fun commitToLocalLFS() {
        SvnTestServer.createEmpty(null, false, SvnTestServer.LfsMode.Local).use { server ->
            val svnRepository: SVNRepository = server.openSvnRepository()
            SvnTestHelper.createFile(svnRepository, ".gitattributes", "* -text\n*.txt filter=lfs diff=lfs merge=lfs -text", SvnFilePropertyTest.propsBinary)
            val data = bigFile()
            SvnTestHelper.createFile(svnRepository, "1.txt", data, SvnFilePropertyTest.propsBinary)
            SvnTestHelper.checkFileContent(svnRepository, "1.txt", data)
            val lockHolder = Holder<SVNLock?>(null)
            svnRepository.lock(Collections.singletonMap("1.txt", svnRepository.latestRevision), null, false, object : ISVNLockHandler {
                override fun handleLock(path: String, lock: SVNLock?, error: SVNErrorMessage?) {
                    Assert.assertEquals(path, "/1.txt")
                    lockHolder.set(lock)
                }

                override fun handleUnlock(path: String, lock: SVNLock?, error: SVNErrorMessage?) {
                    Assert.fail()
                }
            })
            val lock = lockHolder.get()
            Assert.assertNotNull(lock)
            Assert.assertEquals(lock!!.path, "/1.txt")
            Assert.assertNotNull(lock.id)
            Assert.assertEquals(lock.owner, SvnTestServer.USER_NAME)
            SvnTestHelper.createFile(svnRepository, "empty.txt", GitRepository.emptyBytes, SvnFilePropertyTest.propsBinary)
            SvnTestHelper.checkFileContent(svnRepository, "empty.txt", GitRepository.emptyBytes)
        }
    }

    @Test(dataProvider = "compressProvider")
    fun simple(compress: Boolean) {
        val user: User = User.anonymous
        val tempDir = TestHelper.createTempDir("git-as-svn")
        try {
            val storage = LfsLocalStorage(ConcurrentSkipListMap(), LocalLfsConfig.LfsLayout.TwoLevels, tempDir.resolve("data"), tempDir.resolve("meta"), compress)
            // Check file is not exists
            Assert.assertNull(storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308", -1))
            storage.getWriter(user).use { writer ->
                writer.write("Hello, world!!!".toByteArray(StandardCharsets.UTF_8))
                Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308")
            }

            // Read old file.
            val reader = storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308", -1)
            Assert.assertNotNull(reader)
            Assert.assertEquals("9fe77772b085e3533101d59d33a51f19", reader!!.md5)
            Assert.assertEquals(15, reader.size)
            reader.openStream().use { stream -> Assert.assertEquals(CharStreams.toString(InputStreamReader(stream, StandardCharsets.UTF_8)), "Hello, world!!!") }
            checkLfs(storage, user)
            checkLfs(storage, user)
            checkLocks(storage, user)
        } finally {
            TestHelper.deleteDirectory(tempDir)
        }
    }

    @Test(dataProvider = "compressProvider")
    fun nometa(compress: Boolean) {
        val tempDir = TestHelper.createTempDir("git-as-svn")
        try {
            val storage = LfsLocalStorage(ConcurrentSkipListMap(), LocalLfsConfig.LfsLayout.GitLab, tempDir.resolve("data"), null, compress)
            // Check file is not exists
            Assert.assertNull(storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308", -1))
            storage.getWriter(User.anonymous).use { writer ->
                writer.write("Hello, world!!!".toByteArray(StandardCharsets.UTF_8))
                Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308")
            }

            // Read old file.
            val reader = storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308", -1)
            Assert.assertNotNull(reader)
            Assert.assertNull(reader!!.md5)
            Assert.assertEquals(15, reader.size)
            reader.openStream().use { stream -> Assert.assertEquals(CharStreams.toString(InputStreamReader(stream, StandardCharsets.UTF_8)), "Hello, world!!!") }
        } finally {
            TestHelper.deleteDirectory(tempDir)
        }
    }

    @Test(dataProvider = "compressProvider")
    fun alreadyAdded(compress: Boolean) {
        val tempDir = TestHelper.createTempDir("git-as-svn")
        try {
            val storage = LfsLocalStorage(ConcurrentSkipListMap(), LocalLfsConfig.LfsLayout.TwoLevels, tempDir.resolve("data"), tempDir.resolve("meta"), compress)
            // Check file is not exists
            Assert.assertNull(storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308", -1))
            storage.getWriter(User.anonymous).use { writer ->
                writer.write("Hello, world!!!".toByteArray(StandardCharsets.UTF_8))
                Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308")
            }
            storage.getWriter(User.anonymous).use { writer ->
                writer.write("Hello, world!!!".toByteArray(StandardCharsets.UTF_8))
                Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308")
            }
        } finally {
            TestHelper.deleteDirectory(tempDir)
        }
    }

    companion object {
        @JvmStatic
        @DataProvider(name = "compressProvider")
        fun compressProvider(): Array<Array<out Any>> {
            return arrayOf(arrayOf(true), arrayOf(false))
        }

        fun bigFile(): ByteArray {
            val data = ByteArray(TemporaryOutputStream.MAX_MEMORY_SIZE * 2)
            for (i in data.indices) {
                data[i] = (i % 256).toByte()
            }
            return data
        }
        fun checkLfs(storage: LfsStorage, user: User) {
            checkLfs(storage, user, bigFile())
            checkLfs(storage, user, GitRepository.emptyBytes)
        }
        fun checkLocks(storage: LfsStorage, user: User) {
            val locks1: Array<LockDesc> = try {
                storage.lock(user, null, null, false, arrayOf(LockTarget("/dir/1.txt", 1)))
            } catch (e: RequestException) {
                if (e.statusCode == HttpServletResponse.SC_NOT_FOUND) // LFS locks are not supported
                    return
                throw e
            }
            Assert.assertEquals(locks1.size, 1)
            Assert.assertEquals(locks1[0].path, "/dir/1.txt")
            val locks2 = storage.getLocks(user, null, "/dir/", null as String?)
            Assert.assertEquals(locks2, locks1)
            val locks3 = storage.unlock(user, null, false, arrayOf(UnlockTarget("/dir/1.txt", locks1[0].token)))
            Assert.assertEquals(locks3, locks1)
            val otherUserLock = storage.lock(User.anonymous, null, "2.txt")
            try {
                storage.lock(User.anonymous, null, "2.txt")
                Assert.fail()
            } catch (e: LockConflictException) {
                // expected
            }
            val forceUnlockWithoutToken = storage.unlock(user, null, true, arrayOf(UnlockTarget("/2.txt", null)))
            Assert.assertEquals(forceUnlockWithoutToken, arrayOf(otherUserLock))
        }
        private fun checkLfs(storage: LfsStorage, user: User, expected: ByteArray) {
            val expectedOid = "sha256:" + Hashing.sha256().hashBytes(expected).toString()
            val oid: String
            storage.getWriter(user).use { writer ->
                writer.write(expected)
                oid = writer.finish(null)
            }
            Assert.assertEquals(oid, expectedOid)
            val reader = storage.getReader(oid, expected.size.toLong())
            Assert.assertNotNull(reader)
            val actual: ByteArray
            reader!!.openStream().use { stream -> actual = IOUtils.toByteArray(stream) }
            Assert.assertEquals(actual, expected)
            Assert.assertEquals(reader.size, expected.size.toLong())
        }
    }
}
