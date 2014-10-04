/**
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
import org.testng.internal.junit.ArrayAsserts;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import svnserver.SvnTestServer;

import java.net.ConnectException;
import java.util.Arrays;

/**
 * Check file properties.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ShutdownTest {
  private static final int SHOWDOWN_TIME = 5000;
  private static final int FORCE_TIME = 1;

  /**
   * Check simple shutdown:
   * <p>
   * * All old connection have a small time to finish work.
   * * New connection is not accepted.
   *
   * @throws Exception
   */
  @Test
  public void simpleShutdown() throws Exception {
    final String[] oldThreads = getAllThreads();
    final SvnTestServer server = SvnTestServer.createEmpty();
    final SVNRepository repo1 = server.openSvnRepository();
    final SVNRepository repo2 = server.openSvnRepository();
    repo1.getLatestRevision();
    final ISVNEditor editor = repo1.getCommitEditor("Empty commit", null, false, null);
    editor.openRoot(-1);
    server.startShutdown();
    try {
      // Can't create new connection is shutdown mode.
      repo2.getLatestRevision();
      Assert.fail();
    } catch (SVNException e) {
      Throwable cause = e.getCause();
      Assert.assertTrue(cause instanceof ConnectException);
    }
    editor.closeDir();
    editor.closeEdit();
    repo1.closeSession();
    server.shutdown(SHOWDOWN_TIME);
    final String[] newThreads = getAllThreads();
    ArrayAsserts.assertArrayEquals(newThreads, oldThreads);
  }

  /**
   * Check simple shutdown:
   * <p>
   * * All old connection have a small time to finish work.
   * * New connection is not accepted.
   *
   * @throws Exception
   */
  @Test
  public void timeoutShutdown() throws Exception {
    final String[] oldThreads = getAllThreads();
    final SvnTestServer server = SvnTestServer.createEmpty();
    final SVNRepository repo = server.openSvnRepository();
    repo.getLatestRevision();
    final ISVNEditor editor = repo.getCommitEditor("Empty commit", null, false, null);
    editor.openRoot(-1);
    server.startShutdown();
    server.shutdown(FORCE_TIME);
    final String[] newThreads = getAllThreads();
    ArrayAsserts.assertArrayEquals(newThreads, oldThreads);
    try {
      editor.closeDir();
      editor.closeEdit();
      repo.closeSession();
    } catch (SVNException ignored) {
    }
  }

  @NotNull
  private static String[] getAllThreads() {
    for (int count = Thread.activeCount() + 1; ; count *= 2) {
      Thread[] threads = new Thread[count];
      final int size = Thread.enumerate(threads);
      if (size < threads.length) {
        final String[] result = new String[size];
        for (int i = 0; i < size; ++i) {
          result[i] = threads[i].getId() + "#" + threads[i].getName();
        }
        Arrays.sort(result);
        return result;
      }
    }
  }
}
