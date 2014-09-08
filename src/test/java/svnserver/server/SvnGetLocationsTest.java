package svnserver.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import svnserver.SvnTestServer;

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
  /**
   * Check file move log test.
   */
  @Test
  public void moveDirectory() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
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
      final long last = repo.getLatestRevision();

      checkGetLocations(repo, "/baz/test.txt", last, 5, "/baz/test.txt");
      checkGetLocations(repo, "/baz/test.txt", last, 4, "/baz/test.txt");
      checkGetLocations(repo, "/baz/test.txt", last, 3, "/bar/test.txt");
      checkGetLocations(repo, "/baz/test.txt", last, 2, "/bar/test.txt");
      checkGetLocations(repo, "/baz/test.txt", last, 1, "/foo/test.txt");
      checkGetLocations(repo, "/baz/test.txt", last, 0, null);

      checkGetLocations(repo, "/bar/test.txt", 3, 4, null);
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
}
