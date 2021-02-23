/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.network

import com.google.common.hash.Hashing
import com.google.common.io.ByteStreams
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.message.BasicNameValuePair
import org.eclipse.jgit.util.Holder
import org.mapdb.DBMaker.memoryDB
import org.testng.Assert
import org.testng.annotations.Test
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNLock
import org.tmatesoft.svn.core.io.ISVNLockHandler
import org.tmatesoft.svn.core.io.SVNRepository
import ru.bozaro.gitlfs.client.Client
import ru.bozaro.gitlfs.client.auth.CachedAuthProvider
import ru.bozaro.gitlfs.client.exceptions.RequestException
import ru.bozaro.gitlfs.common.JsonHelper
import ru.bozaro.gitlfs.common.data.Link
import ru.bozaro.gitlfs.common.data.Operation
import svnserver.SvnTestHelper
import svnserver.SvnTestServer
import svnserver.VcsAccessEveryone
import svnserver.VcsAccessNoAnonymous
import svnserver.auth.LocalUserDB
import svnserver.auth.User
import svnserver.auth.UserDB
import svnserver.config.SharedConfig
import svnserver.context.LocalContext
import svnserver.context.SharedContext
import svnserver.ext.gitlfs.server.LfsServer
import svnserver.ext.gitlfs.storage.LfsStorage
import svnserver.ext.gitlfs.storage.LfsStorageFactory
import svnserver.ext.gitlfs.storage.local.LfsLocalStorageTest
import svnserver.ext.gitlfs.storage.memory.LfsMemoryStorage
import svnserver.ext.web.config.WebServerConfig
import svnserver.ext.web.server.WebServer
import svnserver.repository.VcsAccess
import svnserver.server.SvnFilePropertyTest
import java.io.IOException
import java.net.URI
import java.nio.file.Paths
import java.util.*

/**
 * Simple test for LfsLocalStorage.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class LfsHttpStorageTest {
    @Test
    @Throws(Exception::class)
    fun commitToRemoteLfs() {
        // Create users
        val users = LocalUserDB()
        val user = users.add(SvnTestServer.USER_NAME, "test", "Test User", "test@example.com")
        Assert.assertNotNull(user)
        SharedContext.create(Paths.get("/nonexistent"), "realm", memoryDB().make(), listOf(WebServerConfig(0))).use { sharedContext ->
            val webServer = sharedContext.sure(WebServer::class.java)
            sharedContext.add(LfsServer::class.java, LfsServer("t0ken", 0, 0F))
            sharedContext.add(UserDB::class.java, users)
            sharedContext.ready()
            // Create local context
            val localContext = LocalContext(sharedContext, "example")
            localContext.add(VcsAccess::class.java, VcsAccessEveryone())
            val backendStorage = LfsMemoryStorage()
            localContext.add(LfsStorage::class.java, backendStorage)
            // Register storage
            sharedContext.sure(LfsServer::class.java).register(localContext, localContext.sure(LfsStorage::class.java))
            val url = webServer.getBaseUrl().resolve("example.git/").resolve(LfsServer.SERVLET_AUTH)
            SvnTestServer.createEmpty(null, false, SvnTestServer.LfsMode.None, GitAsSvnLfsHttpStorage(url, user!!)).use { server ->
                val svnRepository: SVNRepository = server.openSvnRepository()
                SvnTestHelper.createFile(svnRepository, ".gitattributes", "* -text\n*.txt filter=lfs diff=lfs merge=lfs -text", SvnFilePropertyTest.propsBinary)
                val data: ByteArray = LfsLocalStorageTest.bigFile()
                SvnTestHelper.createFile(svnRepository, "1.txt", data, SvnFilePropertyTest.propsBinary)
                SvnTestHelper.checkFileContent(svnRepository, "1.txt", data)
                Assert.assertEquals(backendStorage.files.size, 1)
                val lfsBytes = backendStorage.files.values.iterator().next()
                Assert.assertNotNull(lfsBytes)
                Assert.assertEquals(lfsBytes, data)
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
                Assert.assertEquals(backendStorage.locks.size, 1)
                try {
                    SvnTestHelper.modifyFile(svnRepository, "1.txt", "blabla", -1, null)
                    Assert.fail()
                } catch (e: SVNException) {
                    Assert.assertEquals(e.errorMessage.errorCode, SVNErrorCode.FS_BAD_LOCK_TOKEN)
                }
                SvnTestHelper.modifyFile(svnRepository, "1.txt", "blabla", -1, Collections.singletonMap(lock.path, lock.id))
                Assert.assertEquals(backendStorage.locks.size, 0)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun server() {
        // Create users
        val users = LocalUserDB()
        val user = users.add("test", "test", "Test User", "test@example.com")
        Assert.assertNotNull(user)
        SharedContext.create(Paths.get("/nonexistent"), "realm", memoryDB().make(), listOf(WebServerConfig(0))).use { sharedContext ->
            val webServer = sharedContext.sure(WebServer::class.java)
            sharedContext.add(LfsServer::class.java, LfsServer("t0ken", 0, 0F))
            sharedContext.add(UserDB::class.java, users)
            sharedContext.ready()
            // Create local context
            val localContext = LocalContext(sharedContext, "example")
            localContext.add(VcsAccess::class.java, VcsAccessNoAnonymous())
            localContext.add(LfsStorage::class.java, LfsMemoryStorage())
            // Register storage
            sharedContext.sure(LfsServer::class.java).register(localContext, localContext.sure(LfsStorage::class.java))
            val data: ByteArray = LfsLocalStorageTest.bigFile()
            val oid = "sha256:" + Hashing.sha256().hashBytes(data).toString()
            val url = webServer.getBaseUrl().resolve("example.git/").resolve(LfsServer.SERVLET_AUTH)
            val storage: LfsHttpStorage = GitAsSvnLfsHttpStorage(url, user!!)

            // Check file is not exists
            Assert.assertNull(storage.getReader(oid, -1))
            storage.getWriter(user).use { writer ->
                writer.write(data)
                Assert.assertEquals(writer.finish(null), oid)
            }
            storage.getWriter(user).use { writer ->
                writer.write(data)
                Assert.assertEquals(writer.finish(null), oid)
            }

            // Read old file.
            val reader = storage.getReader(oid, -1)
            Assert.assertNotNull(reader)
            Assert.assertNull(reader!!.md5)
            Assert.assertEquals(reader.size, data.size.toLong())
            reader.openStream().use { stream ->
                val actual = ByteStreams.toByteArray(stream)
                Assert.assertEquals(actual, data)
            }
            LfsLocalStorageTest.checkLfs(storage, user)
            LfsLocalStorageTest.checkLfs(storage, user)
            LfsLocalStorageTest.checkLocks(storage, user)
        }
    }

    private class GitAsSvnLfsHttpStorage(private val authUrl: URI, private val lfsUser: User) : LfsHttpStorage(), LfsStorageFactory, SharedConfig {
        override fun createStorage(context: LocalContext): LfsStorage {
            return this
        }

        override fun lfsClient(user: User): Client {
            val httpClient = createHttpClient()
            val authProvider: CachedAuthProvider = object : CachedAuthProvider() {
                @Throws(IOException::class)
                override fun getAuthUncached(operation: Operation): Link {
                    val post = HttpPost(authUrl)
                    val params = ArrayList<NameValuePair>()
                    addParameter(params, "secretToken", "t0ken")
                    if (lfsUser.isAnonymous) {
                        addParameter(params, "mode", "anonymous")
                    } else {
                        addParameter(params, "mode", "username")
                        addParameter(params, "userId", lfsUser.username)
                    }
                    post.entity = UrlEncodedFormEntity(params)
                    try {
                        val response: HttpResponse = httpClient.execute(post)
                        if (response.statusLine.statusCode == HttpStatus.SC_OK) {
                            return JsonHelper.mapper.readValue(response.entity.content, Link::class.java)
                        }
                        throw RequestException(post, response)
                    } finally {
                        post.abort()
                    }
                }

                private fun addParameter(params: MutableList<NameValuePair>, key: String, value: String?) {
                    if (value != null) {
                        params.add(BasicNameValuePair(key, value))
                    }
                }
            }
            return Client(authProvider, httpClient)
        }

        override fun create(context: SharedContext) {
            context.add(LfsStorageFactory::class.java, this)
        }

        override fun close() {}
    }
}
