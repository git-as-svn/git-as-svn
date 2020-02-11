/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server;

import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import svnserver.SvnTestServer;

import java.util.Map;
import java.util.TreeMap;

/**
 * Check file properties.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class ShutdownTest {
  private static final int SHOWDOWN_TIME = 5000;
  private static final int FORCE_TIME = 1;
  private static final int JOIN_TIME = 100;

  /**
   * Check simple shutdown:
   * <p>
   * * All old connection have a small time to finish work.
   * * New connection is not accepted.
   */
  @Test
  public void simpleShutdown() throws Exception {
    final Map<String, Thread> oldThreads = getAllThreads();
    final SvnTestServer server = SvnTestServer.createEmpty();
    final SVNRepository repo2 = server.openSvnRepository();
    final SVNRepository repo1 = server.openSvnRepository();
    repo1.getLatestRevision();
    final ISVNEditor editor = repo1.getCommitEditor("Empty commit", null, false, null);
    editor.openRoot(-1);
    server.startShutdown();

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
    */
    Thread.sleep(1);

    try {
      // Can't create new connection is shutdown mode.
      repo2.getLatestRevision();
      Assert.fail();
    } catch (SVNException ignored) {
    }
    editor.closeDir();
    editor.closeEdit();
    repo1.closeSession();
    repo2.closeSession();
    server.shutdown(SHOWDOWN_TIME);
    checkThreads(oldThreads);
  }

  private static void checkThreads(@NotNull Map<String, Thread> oldThreads) throws InterruptedException {
    final Map<String, Thread> newThreads = getAllThreads();
    for (Map.Entry<String, Thread> entry : newThreads.entrySet()) {
      if (!oldThreads.containsKey(entry.getKey())) {
        entry.getValue().join(JOIN_TIME);
      }
    }
  }

  @NotNull
  private static Map<String, Thread> getAllThreads() {
    for (int count = Thread.activeCount() + 1; ; count *= 2) {
      Thread[] threads = new Thread[count];
      final int size = Thread.enumerate(threads);
      if (size < threads.length) {
        final Map<String, Thread> result = new TreeMap<>();
        for (int i = 0; i < size; ++i) {
          final Thread thread = threads[i];
          result.put(thread.getId() + "#" + thread.getName(), thread);
        }
        return result;
      }
    }
  }

  /**
   * Check simple shutdown:
   * <p>
   * * All old connection have a small time to finish work.
   * * New connection is not accepted.
   */
  @Test
  public void timeoutShutdown() throws Exception {
    final Map<String, Thread> oldThreads = getAllThreads();
    final SvnTestServer server = SvnTestServer.createEmpty();
    final SVNRepository repo = server.openSvnRepository();
    repo.getLatestRevision();
    final ISVNEditor editor = repo.getCommitEditor("Empty commit", null, false, null);
    editor.openRoot(-1);
    server.startShutdown();
    server.shutdown(FORCE_TIME);
    checkThreads(oldThreads);
    try {
      editor.closeDir();
      editor.closeEdit();
      repo.closeSession();
    } catch (SVNException ignored) {
    }
  }
}
