/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.tester

import org.testng.ITestContext
import org.testng.ITestListener
import org.testng.ITestResult
import org.tmatesoft.svn.core.SVNAuthenticationException
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import svnserver.SvnTestHelper
import svnserver.TestHelper
import java.io.FileWriter
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Path
import java.text.MessageFormat
import java.util.concurrent.TimeUnit

/**
 * Listener for creating SvnTesterExternal.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class SvnTesterExternalListener : ITestListener {
    override fun onTestStart(result: ITestResult) {}
    override fun onTestSuccess(result: ITestResult) {}
    override fun onTestFailure(result: ITestResult) {}
    override fun onTestSkipped(result: ITestResult) {}
    override fun onTestFailedButWithinSuccessPercentage(result: ITestResult) {}
    override fun onStart(context: ITestContext) {
        try {
            val svnserve = SvnTestHelper.findExecutable("svnserve")
            val svnadmin = SvnTestHelper.findExecutable("svnadmin")
            if (svnserve != null && svnadmin != null) {
                log.warn("Native svn daemon executables: {}, {}", svnserve, svnadmin)
                daemon = NativeDaemon(svnserve, svnadmin)
            } else {
                log.warn("Native svn daemon disabled")
            }
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }

    override fun onFinish(context: ITestContext) {
        if (daemon != null) {
            try {
                daemon!!.close()
            } catch (e: Exception) {
                throw IllegalStateException(e)
            } finally {
                daemon = null
            }
        }
    }

    private class NativeDaemon(svnserve: String, svnadmin: String) : SvnTesterFactory, AutoCloseable {
        private val daemon: Process
        private val repo: Path
        private val url: SVNURL

        @Throws(IOException::class)
        private fun detectPort(): Int {
            ServerSocket(0, 0, InetAddress.getByName(HOST)).use { socket -> return socket.localPort }
        }

        @Throws(Exception::class)
        override fun create(): SvnTester {
            return SvnTesterExternal(url, BasicAuthenticationManager.newInstance(USER_NAME, PASSWORD.toCharArray()))
        }

        @Throws(Exception::class)
        override fun close() {
            log.info("Stopping native svn daemon.")
            daemon.destroy()
            daemon.waitFor()
            TestHelper.deleteDirectory(repo)
        }

        companion object {
            @Throws(IOException::class)
            private fun createConfigs(repo: Path): Path {
                val config = repo.resolve("conf/server.conf")
                val passwd = repo.resolve("conf/server.passwd")
                FileWriter(config.toFile()).use { writer -> writer.write(MessageFormat.format(CONFIG_SERVER, passwd.toString())) }
                FileWriter(passwd.toFile()).use { writer -> writer.write(MessageFormat.format(CONFIG_PASSWD, USER_NAME, PASSWORD)) }
                return config
            }
        }

        init {
            val port = detectPort()
            url = SVNURL.create("svn", null, HOST, port, null, true)
            repo = TestHelper.createTempDir("git-as-svn-repo")
            log.info("Starting native svn daemon at: {}, url: {}", repo, url)
            Runtime.getRuntime().exec(
                arrayOf(
                    svnadmin,
                    "create",
                    repo.toString()
                )
            ).waitFor()
            val config = createConfigs(repo)
            daemon = Runtime.getRuntime().exec(
                arrayOf(
                    svnserve,
                    "--daemon",
                    "--root", repo.toString(),
                    "--config-file", config.toString(),
                    "--listen-host", HOST,
                    "--listen-port", port.toString()
                )
            )
            val serverStartupTimeout = System.currentTimeMillis() + SERVER_STARTUP_TIMEOUT
            while (true) {
                try {
                    SVNRepositoryFactory.create(url).getRevisionPropertyValue(0, "example")
                } catch (ignored: SVNAuthenticationException) {
                    break
                } catch (e: SVNException) {
                    if (e.errorMessage.errorCode === SVNErrorCode.RA_SVN_IO_ERROR && System.currentTimeMillis() < serverStartupTimeout) {
                        Thread.sleep(SERVER_STARTUP_DELAY)
                        continue
                    }
                    throw e
                }
                break
            }
        }
    }

    companion object {
        private val log = TestHelper.logger
        private const val USER_NAME = "tester"
        private const val PASSWORD = "passw0rd"
        private const val HOST = "127.0.0.2"
        private const val CONFIG_SERVER = "" +
                "[general]\n" +
                "anon-access = none\n" +
                "auth-access = write\n" +
                "password-db = {0}\n"
        private const val CONFIG_PASSWD = "" +
                "[users]\n" +
                "{0} = {1}\n"
        private val SERVER_STARTUP_TIMEOUT = TimeUnit.SECONDS.toMillis(30)
        private val SERVER_STARTUP_DELAY = TimeUnit.MILLISECONDS.toMillis(20)
        private var daemon: NativeDaemon? = null
        fun get(): SvnTesterFactory? {
            return daemon
        }
    }
}
