/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.SVNRepository;
import svnserver.SvnTestServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static svnserver.SvnTestHelper.*;

/**
 * Check svn locking.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnLockTest {
  /**
   * Check to take lock on absent file.
   *
   * @throws Exception
   */
  @Test
  public void lockNonExists() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      createFile(repo, "/example.txt", "", null);
      lock(repo, "example2.txt", repo.getLatestRevision(), SVNErrorCode.FS_OUT_OF_DATE);
    }
  }

  /**
   * Check to take lock of out-of-date file.
   *
   * @throws Exception
   */
  @Test
  public void lockOutOfDate() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      createFile(repo, "/example.txt", "", null);
      final long latestRevision = repo.getLatestRevision();

      modifyFile(repo, "/example.txt", "content", latestRevision);
      lock(repo, "example.txt", latestRevision, SVNErrorCode.FS_OUT_OF_DATE);
    }
  }

  /**
   * Check to take lock of out-of-date file.
   *
   * @throws Exception
   */
  @Test
  public void lockNotFile() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      final ISVNEditor editor = repo.getCommitEditor("Intital state", null, false, null);
      editor.openRoot(-1);
      editor.addDir("/example", null, -1);
      editor.addFile("/example/example.txt", null, -1);
      sendDeltaAndClose(editor, "/example/example.txt", null, "Source content");
      editor.closeDir();
      editor.closeDir();
      editor.closeEdit();

      final long latestRevision = repo.getLatestRevision();
      try {
        lock(repo, "example", latestRevision, null);
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertEquals(SVNErrorCode.FS_NOT_FILE, e.getErrorMessage().getErrorCode());
      }
    }
  }

  /**
   * Check to take lock of out-of-date file.
   *
   * @throws Exception
   */
  @Test
  public void lockSimple() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      createFile(repo, "/example.txt", "", null);
      final long latestRevision = repo.getLatestRevision();

      createFile(repo, "/example2.txt", "", null);

      // New lock
      final SVNLock lock = lock(repo, "example.txt", latestRevision, null);
      Assert.assertNotNull(lock);
      compareLock(repo.getLock("example.txt"), lock);

      // Already locked
      lock(repo, "example.txt", latestRevision, SVNErrorCode.FS_PATH_ALREADY_LOCKED);

      // Lock must not changed
      compareLock(repo.getLock("example.txt"), lock);

      unlock(repo, lock, null);

      Assert.assertNull(repo.getLock("example.txt"));

      // Lock again
      lock(repo, "example.txt", latestRevision, null);
    }
  }

  /**
   * Check for deny modify locking file.
   *
   * @throws Exception
   */
  @Test
  public void modifyLocked() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      createFile(repo, "/example.txt", "", null);
      final long latestRevision = repo.getLatestRevision();

      // Lock
      final SVNLock lock = lock(repo, "example.txt", latestRevision, null);
      Assert.assertNotNull(lock);

      try {
        modifyFile(repo, "/example.txt", "content", latestRevision);
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertEquals(e.getErrorMessage().getErrorCode(), SVNErrorCode.FS_BAD_LOCK_TOKEN);
      }
    }
  }

  /**
   * Check for deny modify locking file.
   *
   * @throws Exception
   */
  @Test
  public void deleteLocked() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      createFile(repo, "/example.txt", "", null);
      final long latestRevision = repo.getLatestRevision();

      // Lock
      final SVNLock lock = lock(repo, "example.txt", latestRevision, null);
      Assert.assertNotNull(lock);

      try {
        final ISVNEditor editor = repo.getCommitEditor("Intital state", null, false, null);
        editor.openRoot(-1);
        editor.deleteEntry("/example.txt", latestRevision);
        editor.closeDir();
        editor.closeEdit();
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertEquals(e.getErrorMessage().getErrorCode(), SVNErrorCode.FS_BAD_LOCK_TOKEN);
      }
    }
  }

  /**
   * Check for deny modify locking file.
   *
   * @throws Exception
   */
  @Test
  public void deleteLockedDir() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      {
        final ISVNEditor editor = repo.getCommitEditor("Intital state", null, false, null);
        editor.openRoot(-1);
        editor.addDir("/example", null, -1);
        editor.addFile("/example/example.txt", null, -1);
        sendDeltaAndClose(editor, "/example/example.txt", null, "Source content");
        editor.closeDir();
        editor.closeDir();
        editor.closeEdit();
      }
      final long latestRevision = repo.getLatestRevision();
      // Lock
      final SVNLock lock = lock(repo, "/example/example.txt", latestRevision, null);
      Assert.assertNotNull(lock);
      try {
        final ISVNEditor editor = repo.getCommitEditor("Intital state", null, false, null);
        editor.openRoot(-1);
        editor.deleteEntry("/example/example.txt", latestRevision);
        editor.closeDir();
        editor.closeEdit();
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertEquals(e.getErrorMessage().getErrorCode(), SVNErrorCode.FS_BAD_LOCK_TOKEN);
      }
    }
  }

  /**
   * Try to twice remove lock.
   *
   * @throws Exception
   */
  @Test
  public void unlockTwice() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      createFile(repo, "/example.txt", "", null);
      final long latestRevision = repo.getLatestRevision();

      // New lock
      final SVNLock lock = lock(repo, "example.txt", latestRevision, null);
      Assert.assertNotNull(lock);
      unlock(repo, lock, null);
      unlock(repo, lock, SVNErrorCode.FS_NO_SUCH_LOCK);
    }
  }

  /**
   * Try to remove not-owned lock.
   *
   * @throws Exception
   */
  @Test
  public void unlockNotOwner() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      createFile(repo, "/example.txt", "", null);
      final long latestRevision = repo.getLatestRevision();

      // New lock
      final SVNLock oldLock = lock(repo, "example.txt", latestRevision, null);
      Assert.assertNotNull(oldLock);
      unlock(repo, oldLock, null);

      final SVNLock newLock = lock(repo, "example.txt", latestRevision, null);
      Assert.assertNotNull(newLock);
      unlock(repo, oldLock, SVNErrorCode.FS_NO_SUCH_LOCK);
    }
  }

  private void compareLock(@Nullable SVNLock actual, @Nullable SVNLock expeted) {
    if (expeted == null) {
      Assert.assertNull(actual);
    } else {
      Assert.assertNotNull(actual);
      Assert.assertEquals(actual.getID(), expeted.getID());
    }
  }

  @Nullable
  private SVNLock lock(@NotNull SVNRepository repo, @NotNull String path, long revision, @Nullable SVNErrorCode errorCode) throws SVNException {
    final Map<String, Long> pathsToRevisions = new HashMap<>();
    pathsToRevisions.put(path, revision);
    final List<SVNLock> locks = new ArrayList<>();
    repo.lock(pathsToRevisions, null, false, new ISVNLockHandler() {
      @Override
      public void handleLock(@NotNull String path, @Nullable SVNLock lock, @Nullable SVNErrorMessage error) throws SVNException {
        if (errorCode == null) {
          Assert.assertNull(error);
          Assert.assertNotNull(lock);
          locks.add(lock);
        } else {
          Assert.assertNotNull(error);
          Assert.assertEquals(error.getErrorCode(), errorCode);
        }
      }

      @Override
      public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
        Assert.fail();
      }
    });
    Assert.assertTrue(locks.size() <= 1);
    return locks.isEmpty() ? null : locks.get(0);
  }

  private void unlock(@NotNull SVNRepository repo, @NotNull SVNLock lock, @Nullable SVNErrorCode errorCode) throws SVNException {
    final Map<String, String> pathsToTokens = new HashMap<>();
    pathsToTokens.put(lock.getPath(), lock.getID());
    repo.unlock(pathsToTokens, false, new ISVNLockHandler() {
      @Override
      public void handleLock(@NotNull String path, @Nullable SVNLock lock, @Nullable SVNErrorMessage error) throws SVNException {
        Assert.fail();
      }

      @Override
      public void handleUnlock(String path, SVNLock removedLock, SVNErrorMessage error) throws SVNException {
        if (errorCode == null) {
          Assert.assertNull(error);
          Assert.assertNotNull(removedLock);
          compareLock(removedLock, lock);
        } else {
          Assert.assertNotNull(error);
          Assert.assertEquals(error.getErrorCode(), errorCode);
        }
      }
    });
  }
}
