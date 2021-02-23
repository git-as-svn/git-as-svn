/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ldap

import org.testng.Assert
import org.testng.SkipException
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import org.tmatesoft.svn.core.SVNAuthenticationException
import org.tmatesoft.svn.core.SVNException
import svnserver.SvnTestHelper
import svnserver.SvnTestServer
import svnserver.auth.UserDB
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LDAP authentication test.
 *
 * @author Artem V. Navrotskiy (bozaro at buzzsoft.ru)
 */
class AuthLdapTest {
    /**
     * Test for #156, #242.
     */
    @Test(dataProvider = "sslModes")
    @Throws(Exception::class)
    fun nativeClient(serverNet: DirectoryServerNet) {
        val svn = SvnTestHelper.findExecutable("svn") ?: throw SkipException("Native svn executable not found")
        EmbeddedDirectoryServer.create(serverNet).use { ldap ->
            SvnTestServer.createEmpty(ldap.createUserConfig(), false).use { server ->
                val command = arrayOf(svn, "--non-interactive", "ls", "--username=" + EmbeddedDirectoryServer.ADMIN_USERNAME, "--password=" + EmbeddedDirectoryServer.ADMIN_PASSWORD, server.url.toString())
                val exitCode = ProcessBuilder(*command)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .start()
                    .waitFor()
                Assert.assertEquals(exitCode, 0)
            }
        }
    }

    @Test(dataProvider = "sslModes")
    @Throws(Throwable::class)
    fun validUser(serverNet: DirectoryServerNet) {
        checkUser(EmbeddedDirectoryServer.ADMIN_USERNAME, EmbeddedDirectoryServer.ADMIN_PASSWORD, serverNet)
    }

    @Throws(Exception::class)
    private fun checkUser(login: String, password: String, serverNet: DirectoryServerNet) {
        EmbeddedDirectoryServer.create(serverNet).use { ldap -> SvnTestServer.createEmpty(ldap.createUserConfig(), false).use { server -> server.openSvnRepository(login, password).latestRevision } }
    }

    @Test
    @Throws(Throwable::class)
    fun validUserPooled() {
        EmbeddedDirectoryServer.create(RawDirectoryServerNet.instance).use { ldap ->
            SvnTestServer.createEmpty(ldap.createUserConfig(), false).use { server ->
                val pool = Executors.newFixedThreadPool(10)
                val done = AtomicBoolean(false)
                val userDB = server.context.sure(UserDB::class.java)
                val tasks = ArrayList<Callable<Unit>>()
                for (i in 0..999) {
                    tasks.add(SuccessAuth(userDB, done, EmbeddedDirectoryServer.ADMIN_USERNAME, EmbeddedDirectoryServer.ADMIN_PASSWORD))
                    tasks.add(SuccessAuth(userDB, done, "simple", "simple"))
                    tasks.add(InvalidAuth(userDB, done, "simple", "hacker"))
                }
                try {
                    for (future in pool.invokeAll(tasks)) {
                        Assert.assertFalse(done.get())
                        future[300, TimeUnit.SECONDS]
                    }
                } finally {
                    done.set(true)
                    pool.shutdown()
                }
            }
        }
    }

    @Test(dataProvider = "sslModes")
    fun invalidPassword(serverNet: DirectoryServerNet) {
        Assert.expectThrows(SVNAuthenticationException::class.java) { checkUser(EmbeddedDirectoryServer.ADMIN_USERNAME, "wrongpassword", serverNet) }
    }

    @Test(dataProvider = "sslModes")
    fun invalidUser(serverNet: DirectoryServerNet) {
        Assert.expectThrows(SVNAuthenticationException::class.java) { checkUser("ldapadmin2", EmbeddedDirectoryServer.ADMIN_PASSWORD, serverNet) }
    }

    @Test(dataProvider = "sslModes")
    @Throws(Throwable::class)
    fun anonymousUserAllowed(serverNet: DirectoryServerNet) {
        checkAnonymous(true, serverNet)
    }

    @Throws(Exception::class)
    private fun checkAnonymous(anonymousRead: Boolean, serverNet: DirectoryServerNet) {
        EmbeddedDirectoryServer.create(serverNet).use { ldap -> SvnTestServer.createEmpty(ldap.createUserConfig(), anonymousRead).use { server -> server.openSvnRepository().latestRevision } }
    }

    @Test(dataProvider = "sslModes")
    fun anonymousUserDenies(serverNet: DirectoryServerNet) {
        Assert.expectThrows(SVNAuthenticationException::class.java) { checkAnonymous(false, serverNet) }
    }

    private class SuccessAuth(private val userDB: UserDB, private val done: AtomicBoolean, private val username: String, private val password: String) : Callable<Unit> {
        override fun call() {
            if (done.get()) return
            try {
                val user = userDB.check(username, password)
                Assert.assertEquals(user!!.username, username)
            } catch (e: SVNException) {
                done.set(false)
            }
        }
    }

    private class InvalidAuth(private val userDB: UserDB, private val done: AtomicBoolean, private val username: String, private val password: String) : Callable<Unit> {
        override fun call() {
            if (done.get()) return
            try {
                val user = userDB.check(username, password)
                Assert.assertNull(user)
            } catch (e: SVNException) {
                done.set(false)
            }
        }
    }

    companion object {
        @JvmStatic
        @DataProvider
        @Throws(Exception::class)
        fun sslModes(): Array<Array<out Any>> {
            val cert = Paths.get(AuthLdapTest::class.java.getResource("cert.pem").toURI())
            val key = Paths.get(AuthLdapTest::class.java.getResource("key.pem").toURI())
            return arrayOf(arrayOf(RawDirectoryServerNet.instance), arrayOf(SslDirectoryServerNet(cert, key)))
        }
    }
}
