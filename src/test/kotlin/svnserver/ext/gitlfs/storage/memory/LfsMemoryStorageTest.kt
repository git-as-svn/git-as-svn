/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.memory

import com.google.common.io.CharStreams
import org.testng.*
import org.testng.annotations.*
import org.tmatesoft.svn.core.SVNException
import ru.bozaro.gitlfs.common.LockConflictException
import svnserver.auth.*
import java.io.*
import java.nio.charset.StandardCharsets

/**
 * Simple test for LfsMemoryStorage.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class LfsMemoryStorageTest {
    @Test
    @Throws(IOException::class)
    fun simple() {
        val storage = LfsMemoryStorage()
        // Check file is not exists
        Assert.assertNull(storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308", -1))
        storage.getWriter(User.anonymous).use { writer ->
            writer.write("Hello, world!!!".toByteArray(StandardCharsets.UTF_8))
            Assert.assertEquals(writer.finish(null), "sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308")
        }

        // Read old file.
        val reader = storage.getReader("sha256:61f27ddd5b4e533246eb76c45ed4bf4504daabce12589f97b3285e9d3cd54308", -1)
        Assert.assertNotNull(reader)
        Assert.assertEquals("9fe77772b085e3533101d59d33a51f19", reader!!.md5)
        Assert.assertEquals(15, reader.size)
        reader.openStream().use { stream -> Assert.assertEquals(CharStreams.toString(InputStreamReader(stream, StandardCharsets.UTF_8)), "Hello, world!!!") }
    }

    @Test
    @Throws(SVNException::class, LockConflictException::class, IOException::class)
    fun lockUnlock() {
        val storage = LfsMemoryStorage()
        val lock = storage.lock(User.anonymous, null, "README.md")
        Assert.assertNotNull(lock)
        val locks = storage.getLocks(User.anonymous, null, "README.md", null as String?)
        Assert.assertEquals(locks.size, 1)
        Assert.assertEquals(locks[0], lock)
        val unlock = storage.unlock(User.anonymous, null, false, lock.token)!!
        Assert.assertEquals(unlock, lock)
    }

    @Test
    @Throws(IOException::class)
    fun alreadyAdded() {
        val storage = LfsMemoryStorage()
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
    }
}
