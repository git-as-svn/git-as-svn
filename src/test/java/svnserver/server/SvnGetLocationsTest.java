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
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import svnserver.SvnTestServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static svnserver.SvnTestHelper.modifyFile;
import static svnserver.SvnTestHelper.sendDeltaAndClose;

/**
 * Check file properties.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnGetLocationsTest {
  @Test
  public void segmentsSimple() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      initRepo(repo);

      final long last = repo.getLatestRevision();
      checkGetSegments(repo, "/baz/test.txt", last, 5, 3,
          "/baz/test.txt@4:5",
          "/bar/test.txt@3:3"
      );
      checkGetSegments(repo, "/baz/test.txt", last, 5, 0,
          "/baz/test.txt@4:5",
          "/bar/test.txt@2:3",
          "/foo/test.txt@1:1"
      );
      checkGetSegments(repo, "/baz/test.txt", last, 2, 0,
          "/bar/test.txt@2:2",
          "/foo/test.txt@1:1"
      );
      checkGetSegments(repo, "/bar/test.txt", 3, 2, 1,
          "/bar/test.txt@2:2",
          "/foo/test.txt@1:1"
      );
      checkGetSegments(repo, "/foo/test.txt", 1, 1, 1,
          "/foo/test.txt@1:1"
      );
    }
  }

  @Test
  public void segmentsInvalidRange() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      initRepo(repo);

      final long last = repo.getLatestRevision();
      try {
        checkGetSegments(repo, "/baz/test.txt", last, 2, 5);
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertEquals(e.getErrorMessage().getErrorCode().getCode(), 204900);
      }
    }
  }

  @Test
  public void segmentsNotFound() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      initRepo(repo);

      final long last = repo.getLatestRevision();
      try {
        checkGetSegments(repo, "/baz/test.xml", last, 5, 2);
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertEquals(e.getErrorMessage().getErrorCode(), SVNErrorCode.FS_NOT_FOUND);
      }
    }
  }

  @Test
  public void locationsSimple() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      initRepo(repo);

      final long last = repo.getLatestRevision();
      checkGetLocations(repo, "/baz/test.txt", last, 5, "/baz/test.txt");
      checkGetLocations(repo, "/baz/test.txt", last, 4, "/baz/test.txt");
      checkGetLocations(repo, "/baz/test.txt", last, 3, "/bar/test.txt");
      checkGetLocations(repo, "/baz/test.txt", last, 2, "/bar/test.txt");
      checkGetLocations(repo, "/baz/test.txt", last, 1, "/foo/test.txt");
      checkGetLocations(repo, "/baz/test.txt", last, 0, null);

      checkGetLocations(repo, "/bar/test.txt", 3, 3, "/bar/test.txt");
      checkGetLocations(repo, "/bar/test.txt", 3, 2, "/bar/test.txt");
      checkGetLocations(repo, "/bar/test.txt", 3, 1, "/foo/test.txt");
      checkGetLocations(repo, "/bar/test.txt", 3, 0, null);

      checkGetLocations(repo, "/bar/test.txt", 2, 2, "/bar/test.txt");
      checkGetLocations(repo, "/bar/test.txt", 2, 1, "/foo/test.txt");
      checkGetLocations(repo, "/bar/test.txt", 2, 0, null);

      checkGetLocations(repo, "/foo/test.txt", 1, 1, "/foo/test.txt");
      checkGetLocations(repo, "/foo/test.txt", 1, 0, null);
    }
  }

  @Test
  public void locationsNotFound() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      initRepo(repo);

      try {
        checkGetLocations(repo, "/bar/test.xml", 3, 3, null);
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertEquals(e.getErrorMessage().getErrorCode(), SVNErrorCode.FS_NOT_FOUND);
      }
      try {
        checkGetLocations(repo, "/bar/test.txt", 3, 4, null);
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertEquals(e.getErrorMessage().getErrorCode(), SVNErrorCode.FS_NOT_FOUND);
      }
    }
  }

  private void initRepo(@NotNull SVNRepository repo) throws SVNException, IOException {
    // r1 - add single file.
    {
      final ISVNEditor editor = repo.getCommitEditor("Create directory: /foo", null, false, null);
      editor.openRoot(-1);
      editor.addDir("/foo", null, -1);
      // Some file.
      editor.addFile("/foo/test.txt", null, -1);
      sendDeltaAndClose(editor, "/foo/test.txt", null, "Foo content");
      // Close dir
      editor.closeDir();
      editor.closeDir();
      editor.closeEdit();
    }
    // r2 - rename dir
    {
      final long revision = repo.getLatestRevision();
      final ISVNEditor editor = repo.getCommitEditor("Rename: /foo to /bar", null, false, null);
      editor.openRoot(-1);
      // Move dir.
      editor.addDir("/bar", "/foo", revision);
      editor.closeDir();
      editor.deleteEntry("/foo", revision);
      // Close dir
      editor.closeDir();
      editor.closeEdit();
    }
    // r3 - modify file.
    modifyFile(repo, "/bar/test.txt", "Bar content", repo.getLatestRevision());
    // r4 - rename dir
    {
      final long revision = repo.getLatestRevision();
      final ISVNEditor editor = repo.getCommitEditor("Rename: /bar to /baz", null, false, null);
      editor.openRoot(-1);
      // Move dir.
      editor.addDir("/baz", "/bar", revision);
      editor.closeDir();
      editor.deleteEntry("/bar", revision);
      // Close dir
      editor.closeDir();
      editor.closeEdit();
    }
    // r5 - modify file.
    modifyFile(repo, "/baz/test.txt", "Baz content", repo.getLatestRevision());
  }

  private void checkGetLocations(@NotNull SVNRepository repo, @NotNull String path, long pegRev, long targetRev, @Nullable String expectedPath) throws SVNException {
    final List<String> paths = new ArrayList<>();
    repo.getLocations(path, pegRev, new long[]{targetRev}, locationEntry -> {
      Assert.assertEquals(locationEntry.getRevision(), targetRev);
      paths.add(locationEntry.getPath());
    });
    if (expectedPath == null) {
      Assert.assertTrue(paths.isEmpty());
    } else {
      Assert.assertEquals(paths.size(), 1);
      Assert.assertEquals(paths.get(0), expectedPath);
    }
  }

  private void checkGetSegments(@NotNull SVNRepository repo, @NotNull String path, long pegRev, long startRev, long endRev, @NotNull String... expected) throws SVNException {
    final List<String> actual = new ArrayList<>();
    repo.getLocationSegments(path, pegRev, startRev, endRev, locationEntry -> {
      actual.add(locationEntry.getPath() + "@" + locationEntry.getStartRevision() + ":" + locationEntry.getEndRevision());
    });
    Assert.assertEquals(actual.toArray(new String[actual.size()]), expected);
  }
}
