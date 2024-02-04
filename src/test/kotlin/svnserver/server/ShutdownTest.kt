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
import org.tmatesoft.svn.core.SVNException
import svnserver.SvnTestServer
import java.util.*

/**
 * Check file properties.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class ShutdownTest {
    /**
     * Check simple shutdown:
     *
     *
     * * All old connection have a small time to finish work.
     * * New connection is not accepted.
     */
    @Test
    fun simpleShutdown() {
        val oldThreads = allThreads
        val server: SvnTestServer = SvnTestServer.createEmpty()
        val repo2 = server.openSvnRepository()
        val repo1 = server.openSvnRepository()
        repo1.latestRevision
        val editor = repo1.getCommitEditor("Empty commit", null, false, null)
        editor.openRoot(-1)
        server.startShutdown()

        /*
     Looks like there's a bug in OpenJDK 13 on Linux.
     1. Thread A calls ServerSocket.accept
     2. Thread B calls ServerSocket.close
     3. Thread B tries to connect to this socket
     4. Thread A always gets SocketException("socket closed")
     5. But *sometimes* TCP connection gets established even though
        there's nothing on server side that can talk to it
     This can be reproduced by removing sleep(1) and running this test multiple times.

     Reproduced on:
     openjdk version "13.0.2" 2020-01-14
     OpenJDK Runtime Environment (build 13.0.2+8)
     OpenJDK 64-Bit Server VM (build 13.0.2+8, mixed mode, sharing)
    */Thread.sleep(1)
        try {
            // Can't create new connection is shutdown mode.
            repo2.latestRevision
            Assert.fail()
        } catch (ignored: SVNException) {
        }
        editor.closeDir()
        editor.closeEdit()
        repo1.closeSession()
        repo2.closeSession()
        server.shutdown(SHOWDOWN_TIME)
        checkThreads(oldThreads)
    }

    /**
     * Check simple shutdown:
     *
     *
     * * All old connection have a small time to finish work.
     * * New connection is not accepted.
     */
    @Test
    fun timeoutShutdown() {
        val oldThreads = allThreads
        val server: SvnTestServer = SvnTestServer.createEmpty()
        val repo = server.openSvnRepository()
        repo.latestRevision
        val editor = repo.getCommitEditor("Empty commit", null, false, null)
        editor.openRoot(-1)
        server.startShutdown()
        server.shutdown(FORCE_TIME)
        checkThreads(oldThreads)
        try {
            editor.closeDir()
            editor.closeEdit()
            repo.closeSession()
        } catch (ignored: SVNException) {
        }
    }

    companion object {
        private const val SHOWDOWN_TIME = 5000
        private const val FORCE_TIME = 1
        private const val JOIN_TIME = 100
        private fun checkThreads(oldThreads: Map<String, Thread>) {
            val newThreads = allThreads
            for ((key, value) in newThreads) {
                if (!oldThreads.containsKey(key)) {
                    value.join(JOIN_TIME.toLong())
                }
            }
        }

        private val allThreads: Map<String, Thread>
            get() {
                var count = Thread.activeCount() + 1
                while (true) {
                    val threads = arrayOfNulls<Thread>(count)
                    val size = Thread.enumerate(threads)
                    if (size < threads.size) {
                        val result = TreeMap<String, Thread>()
                        for (i in 0 until size) {
                            val thread = threads[i]
                            result[thread!!.threadId().toString() + "#" + thread.name] = thread
                        }
                        return result
                    }
                    count *= 2
                }
            }
    }
}
