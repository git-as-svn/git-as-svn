/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.*;
import svnserver.StringHelper;
import svnserver.SvnTestServer;
import svnserver.TestHelper;

import java.io.File;
import java.util.*;

import static svnserver.SvnTestHelper.sendDeltaAndClose;

/**
 * Simple checkout tests.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnCheckoutTest {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(SvnCheckoutTest.class);

  @Test
  public void checkoutRootRevision() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SvnOperationFactory factory = server.createOperationFactory();
      final SvnCheckout checkout = factory.createCheckout();
      checkout.setSource(SvnTarget.fromURL(server.getUrl()));
      checkout.setSingleTarget(SvnTarget.fromFile(server.getTempDirectory()));
      checkout.setRevision(SVNRevision.create(0));
      checkout.run();
    }
  }

  /**
   * Workcopy mixed version update smoke test.
   *
   * @throws Exception
   */
  @Test
  public void randomUpdateRoot() throws Exception {
    checkUpdate("");

  }

  /**
   * Workcopy mixed version update smoke test.
   *
   * @throws Exception
   */
  @Test
  public void randomUpdateChild() throws Exception {
    checkUpdate("/src");
  }

  private void checkUpdate(@NotNull String basePath) throws Exception {
    try (SvnTestServer server = SvnTestServer.createMasterRepository()) {
      final SvnOperationFactory factory = server.createOperationFactory();
      factory.setAutoCloseContext(false);
      factory.setAutoDisposeRepositoryPool(false);

      final SVNRepository repo = server.openSvnRepository();
      final List<Long> revisions = loadUpdateRevisions(repo, basePath);
      Assert.assertTrue(revisions.size() > 2);

      final SvnCheckout checkout = factory.createCheckout();
      checkout.setSource(SvnTarget.fromURL(server.getUrl().appendPath(basePath, false)));
      checkout.setSingleTarget(SvnTarget.fromFile(server.getTempDirectory()));
      checkout.setRevision(SVNRevision.create(revisions.get(0)));
      checkout.run();

      factory.setEventHandler(new ISVNEventHandler() {
        @Override
        public void handleEvent(SVNEvent event, double progress) throws SVNException {
          Assert.assertEquals(event.getExpectedAction(), event.getAction());
        }

        @Override
        public void checkCancelled() throws SVNCancelException {
        }
      });
      final Random rand = new Random(0);
      for (long revision : revisions.subList(1, revisions.size())) {
        final SvnLog svnLog = factory.createLog();
        svnLog.setSingleTarget(SvnTarget.fromURL(server.getUrl()));
        svnLog.setRevisionRanges(Collections.singletonList(SvnRevisionRange.create(SVNRevision.create(revision - 1), SVNRevision.create(revision))));
        svnLog.setDiscoverChangedPaths(true);
        final SVNLogEntry logEntry = svnLog.run();
        log.info("Update to revision #{}: {}", revision, StringHelper.getFirstLine(logEntry.getMessage()));

        final TreeMap<String, SVNLogEntryPath> paths = new TreeMap<>(logEntry.getChangedPaths());
        final List<String> targets = new ArrayList<>();
        final SvnUpdate update = factory.createUpdate();
        String lastAdded = null;
        for (Map.Entry<String, SVNLogEntryPath> entry : paths.entrySet()) {
          String path = entry.getKey();
          if ((lastAdded != null) && path.startsWith(lastAdded)) {
            continue;
          }
          if (entry.getValue().getType() == 'A') {
            lastAdded = path + "/";
          }
          if (entry.getValue().getType() == 'A' || rand.nextBoolean()) {
            if (path.startsWith(basePath)) {
              final String subPath = path.substring(basePath.length());
              targets.add(subPath.startsWith("/") ? subPath.substring(1) : subPath);
            }
          }
        }
        if (!targets.isEmpty()) {
          for (String target : targets) {
            update.addTarget(SvnTarget.fromFile(new File(server.getTempDirectory(), target)));
          }
          update.setRevision(SVNRevision.create(revision));
          update.setSleepForTimestamp(false);
          update.setMakeParents(true);
          update.run();
        }
      }
    }
  }

  @NotNull
  private List<Long> loadUpdateRevisions(@NotNull SVNRepository repo, @NotNull String path) throws SVNException {
    final long maxRevision = repo.getLatestRevision();
    final LinkedList<Long> revisions = new LinkedList<>();
    repo.log(new String[]{path}, maxRevision, 0, false, false, logEntry -> revisions.addFirst(logEntry.getRevision()));
    return new ArrayList<>(revisions);
  }

  /**
   * <pre>
   * svn checkout
   * echo > test.txt
   * svn commit -m "create test.txt"
   *   rev N
   * echo foo > test.txt
   * svn commit -m "modify test.txt"
   * svn up rev N
   * </pre>
   *
   * @throws Exception
   */
  @Test
  public void checkoutAndUpdate() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      final ISVNEditor editor = repo.getCommitEditor("Intital state", null, false, null);
      editor.openRoot(-1);
      editor.addDir("/src", null, -1);
      editor.addDir("/src/main", null, -1);
      editor.addFile("/src/main/source.txt", null, -1);
      sendDeltaAndClose(editor, "/src/main/source.txt", null, "Source content");
      editor.closeDir();
      editor.addDir("/src/test", null, -1);
      editor.addFile("/src/test/test.txt", null, -1);
      sendDeltaAndClose(editor, "/src/test/test.txt", null, "Test content");
      editor.closeDir();
      editor.closeDir();
      editor.closeDir();
      editor.closeEdit();

      // checkout
      final SvnOperationFactory factory = server.createOperationFactory();

      final SvnCheckout checkout = factory.createCheckout();
      checkout.setSource(SvnTarget.fromURL(server.getUrl()));
      checkout.setSingleTarget(SvnTarget.fromFile(server.getTempDirectory()));
      checkout.setRevision(SVNRevision.HEAD);
      checkout.run();

      final File file = new File(server.getTempDirectory(), "src/main/someFile.txt");
      final SVNClientManager client = SVNClientManager.newInstance(factory);
      // create file
      final SVNCommitInfo commit;
      {
        Assert.assertFalse(file.exists());
        TestHelper.saveFile(file, "New content");
        client.getWCClient().doAdd(file, false, false, false, SVNDepth.INFINITY, false, true);
        commit = client.getCommitClient().doCommit(new File[]{file}, false, "Commit new file", null, null, false, false, SVNDepth.INFINITY);
      }
      // modify file
      {
        Assert.assertTrue(file.exists());
        TestHelper.saveFile(file, "Modified content");
        client.getCommitClient().doCommit(new File[]{file}, false, "Modify up-to-date commit", null, null, false, false, SVNDepth.INFINITY);
      }
      // update to previous commit
      client.getUpdateClient().doUpdate(server.getTempDirectory(), SVNRevision.create(commit.getNewRevision()), SVNDepth.INFINITY, false, false);
      // check no tree conflist
      ArrayList<String> changeLists = new ArrayList<>();
      client.getStatusClient().doStatus(server.getTempDirectory(), SVNRevision.WORKING, SVNDepth.INFINITY, false, false, true, false, status -> {
        Assert.assertNull(status.getTreeConflict(), status.getFile().toString());
        Assert.assertNull(status.getConflictNewFile(), status.getFile().toString());
      }, changeLists);
      Assert.assertTrue(changeLists.isEmpty());
    }
  }
}
