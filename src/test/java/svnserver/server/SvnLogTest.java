package svnserver.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.Test;
import org.testng.internal.junit.ArrayAsserts;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import svnserver.SvnTestServer;

import java.util.*;

import static svnserver.SvnTestHelper.*;

/**
 * Check file properties.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnLogTest {
  private static class LogEntry {
    private final long revision;
    @Nullable
    private final String message;
    @NotNull
    private final Set<String> paths;

    private LogEntry(@NotNull SVNLogEntry logEntry) {
      this(logEntry.getRevision(), logEntry.getMessage(), convert(logEntry.getChangedPaths().values()));
    }

    private static Collection<String> convert(@NotNull Collection<SVNLogEntryPath> changedPaths) {
      List<String> result = new ArrayList<>();
      for (SVNLogEntryPath logPath : changedPaths) {
        result.add(logPath.getType() + " " + logPath.getPath());
      }
      return result;
    }

    private LogEntry(long revision, @Nullable String message, @NotNull String... paths) {
      this(revision, message, Arrays.asList(paths));
    }

    private LogEntry(long revision, @Nullable String message, @NotNull Collection<String> paths) {
      this.revision = revision;
      this.message = message;
      this.paths = new TreeSet<>(paths);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final LogEntry logEntry = (LogEntry) o;
      return revision == logEntry.revision
          && Objects.equals(message, logEntry.message)
          && paths.equals(logEntry.paths);
    }

    @Override
    public int hashCode() {
      int result = (int) (revision ^ (revision >>> 32));
      if (message != null)
        result = 31 * result + message.hashCode();
      result = 31 * result + paths.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return "LogEntry{" +
          "revision=" + revision +
          ", message='" + message + '\'' +
          ", paths=" + paths +
          '}';
    }
  }

  /**
   * Check simple svn log behaviour.
   *
   * @throws Exception
   */
  @Test
  public void simple() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      // r1 - add single file.
      createFile(repo, "/foo.txt", "", null);
      // r2 - add file in directory.
      {
        final long latestRevision = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Create directory: /foo", null, false, null);
        editor.openRoot(latestRevision);
        editor.addDir("/foo", null, -1);
        editor.addFile("/foo/bar.txt", null, -1);
        sendDeltaAndClose(editor, "/foo/bar.txt", null, "File body");
        editor.closeDir();
        editor.closeDir();
        editor.closeEdit();
      }
      // r3 - change file in directory.
      modifyFile(repo, "/foo/bar.txt", "New body", repo.getLatestRevision());
      // r4 - change file in directory.
      createFile(repo, "/foo/foo.txt", "New body", null);

      // svn log from root
      final long last = repo.getLatestRevision();
      checkLog(repo, last, 0, "/",
          new LogEntry(4, "Create file: /foo/foo.txt", "A /foo/foo.txt"),
          new LogEntry(3, "Modify file: /foo/bar.txt", "M /foo/bar.txt"),
          new LogEntry(2, "Create directory: /foo", "A /foo", "A /foo/bar.txt"),
          new LogEntry(1, "Create file: /foo.txt", "A /foo.txt"),
          new LogEntry(0, null)
      );

      // svn log from root
      checkLog(repo, last, 0, "/foo",
          new LogEntry(4, "Create file: /foo/foo.txt", "A /foo/foo.txt"),
          new LogEntry(3, "Modify file: /foo/bar.txt", "M /foo/bar.txt"),
          new LogEntry(2, "Create directory: /foo", "A /foo", "A /foo/bar.txt")
      );

      // svn log from root
      checkLog(repo, last, 0, "/foo/bar.txt",
          new LogEntry(3, "Modify file: /foo/bar.txt", "M /foo/bar.txt"),
          new LogEntry(2, "Create directory: /foo", "A /foo", "A /foo/bar.txt")
      );

      // svn empty log
      checkLog(repo, 0, 0, "/",
          new LogEntry(0, null)
      );
    }
  }

  /**
   * Check file recreate log test.
   */
  @Test
  public void recreateFile() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      // r1 - add single file.
      createFile(repo, "/foo.txt", "", null);
      // r2 - modify file.
      modifyFile(repo, "/foo.txt", "New content", repo.getLatestRevision());
      // r3 - remove file.
      deleteFile(repo, "/foo.txt");
      final long delete = repo.getLatestRevision();
      // r4 - recreate file.
      createFile(repo, "/foo.txt", "", null);

      // svn log from root
      final long last = repo.getLatestRevision();
      checkLog(repo, last, 0, "/foo.txt",
          new LogEntry(4, "Create file: /foo.txt", "A /foo.txt")
      );

      // svn log from root
      checkLog(repo, delete - 1, 0, "/foo.txt",
          new LogEntry(2, "Modify file: /foo.txt", "M /foo.txt"),
          new LogEntry(1, "Create file: /foo.txt", "A /foo.txt")
      );
    }
  }

  /**
   * Check file recreate log test.
   */
  @Test
  public void recreateDirectory() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      // r1 - add single file.
      {
        final ISVNEditor editor = repo.getCommitEditor("Create directory: /foo", null, false, null);
        editor.openRoot(-1);
        editor.addDir("/foo", null, -1);
        // Empty file.
        final String file = "/foo/bar.txt";
        editor.addFile(file, null, -1);
        sendDeltaAndClose(editor, file, null, "");
        // Close dir
        editor.closeDir();
        editor.closeDir();
        editor.closeEdit();
      }
      // r2 - modify file.
      modifyFile(repo, "/foo/bar.txt", "New content", repo.getLatestRevision());
      // r3 - remove directory.
      deleteFile(repo, "/foo");
      final long delete = repo.getLatestRevision();
      // r4 - recreate file.
      {
        final ISVNEditor editor = repo.getCommitEditor("Create directory: /foo", null, false, null);
        editor.openRoot(-1);
        editor.addDir("/foo", null, -1);
        // Empty file.
        final String file = "/foo/bar.txt";
        editor.addFile(file, null, -1);
        sendDeltaAndClose(editor, file, null, "");
        // Close dir
        editor.closeDir();
        editor.closeDir();
        editor.closeEdit();
      }

      // svn log from latest revision
      final long last = repo.getLatestRevision();
      checkLog(repo, last, 0, "/foo/bar.txt",
          new LogEntry(4, "Create directory: /foo", "A /foo", "A /foo/bar.txt")
      );

      // svn log from revision before delete
      checkLog(repo, delete - 1, 0, "/foo.txt",
          new LogEntry(2, "Modify file: /foo/bar.txt", "M /foo/bar.txt"),
          new LogEntry(1, "Create directory: /foo", "A /foo", "A /foo/bar.txt")
      );
    }
  }

  /**
   * Check file move log test.
   */
  @Test
  public void moveFile() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      // r1 - add single file.
      createFile(repo, "/foo.txt", "Foo content", null);
      // r2 - rename file
      {
        final long revision = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Rename: /foo.txt to /bar.txt", null, false, null);
        editor.openRoot(-1);
        // Empty file.
        editor.addFile("/bar.txt", "/foo.txt", revision);
        editor.closeFile("/bar.txt", null);
        editor.deleteEntry("/foo.txt", revision);
        // Close dir
        editor.closeDir();
        editor.closeEdit();
      }
      // r3 - modify file.
      modifyFile(repo, "/bar.txt", "Bar content", repo.getLatestRevision());
      // r4 - rename file
      {
        final long revision = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Rename: /bar.txt to /baz.txt", null, false, null);
        editor.openRoot(-1);
        // Empty file.
        editor.addFile("/baz.txt", "/bar.txt", revision);
        editor.closeFile("/baz.txt", null);
        editor.deleteEntry("/bar.txt", revision);
        // Close dir
        editor.closeDir();
        editor.closeEdit();
      }
      // r5 - modify file.
      modifyFile(repo, "/baz.txt", "Baz content", repo.getLatestRevision());
      final long last = repo.getLatestRevision();
      // r6 - remove file.
      deleteFile(repo, "/baz.txt");

      // svn log from last file exists revision
      checkLog(repo, last, 0, "/baz.txt",
          new LogEntry(5, "Modify file: /baz.txt", "M /baz.txt"),
          new LogEntry(4, "Rename: /bar.txt to /baz.txt", "D /bar.txt", "A /baz.txt"),
          new LogEntry(3, "Modify file: /bar.txt", "M /bar.txt"),
          new LogEntry(2, "Rename: /foo.txt to /bar.txt", "D /foo.txt", "A /bar.txt"),
          new LogEntry(1, "Create file: /foo.txt", "A /foo.txt")
      );

      // svn log from last file exists revision
      checkLog(repo, 0, last, "/baz.txt",
          new LogEntry(1, "Create file: /foo.txt", "A /foo.txt"),
          new LogEntry(2, "Rename: /foo.txt to /bar.txt", "D /foo.txt", "A /bar.txt"),
          new LogEntry(3, "Modify file: /bar.txt", "M /bar.txt"),
          new LogEntry(4, "Rename: /bar.txt to /baz.txt", "D /bar.txt", "A /baz.txt"),
          new LogEntry(5, "Modify file: /baz.txt", "M /baz.txt")
      );

      // svn log from last file exists revision
      checkLogLimit(repo, last, 0, 3, "/baz.txt",
          new LogEntry(5, "Modify file: /baz.txt", "M /baz.txt"),
          new LogEntry(4, "Rename: /bar.txt to /baz.txt", "D /bar.txt", "A /baz.txt"),
          new LogEntry(3, "Modify file: /bar.txt", "M /bar.txt")
      );

      // svn log from last file exists revision
      checkLogLimit(repo, 0, last, 3, "/baz.txt",
          new LogEntry(1, "Create file: /foo.txt", "A /foo.txt"),
          new LogEntry(2, "Rename: /foo.txt to /bar.txt", "D /foo.txt", "A /bar.txt"),
          new LogEntry(3, "Modify file: /bar.txt", "M /bar.txt")
      );

      // svn log from last file exists revision
      checkLog(repo, 3, 0, "/bar.txt",
          new LogEntry(3, "Modify file: /bar.txt", "M /bar.txt"),
          new LogEntry(2, "Rename: /foo.txt to /bar.txt", "D /foo.txt", "A /bar.txt"),
          new LogEntry(1, "Create file: /foo.txt", "A /foo.txt")
      );
    }
  }

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

      // svn log from last file exists revision
      checkLog(repo, last, 0, "/baz/test.txt",
          new LogEntry(5, "Modify file: /baz/test.txt", "M /baz/test.txt"),
          new LogEntry(4, "Rename: /bar to /baz", "D /bar", "A /baz", "A /baz/test.txt"),
          new LogEntry(3, "Modify file: /bar/test.txt", "M /bar/test.txt"),
          new LogEntry(2, "Rename: /foo to /bar", "D /foo", "A /bar", "A /bar/test.txt"),
          new LogEntry(1, "Create directory: /foo", "A /foo", "A /foo/test.txt")
      );

      // svn log from last file exists revision
      checkLog(repo, 0, last, "/baz/test.txt",
          new LogEntry(1, "Create directory: /foo", "A /foo", "A /foo/test.txt"),
          new LogEntry(2, "Rename: /foo to /bar", "D /foo", "A /bar", "A /bar/test.txt"),
          new LogEntry(3, "Modify file: /bar/test.txt", "M /bar/test.txt"),
          new LogEntry(4, "Rename: /bar to /baz", "D /bar", "A /baz", "A /baz/test.txt"),
          new LogEntry(5, "Modify file: /baz/test.txt", "M /baz/test.txt")
      );

      // svn log from last file exists revision
      checkLogLimit(repo, last, 0, 3, "/baz/test.txt",
          new LogEntry(5, "Modify file: /baz/test.txt", "M /baz/test.txt"),
          new LogEntry(4, "Rename: /bar to /baz", "D /bar", "A /baz", "A /baz/test.txt"),
          new LogEntry(3, "Modify file: /bar/test.txt", "M /bar/test.txt")
      );

      // svn log from last file exists revision
      checkLogLimit(repo, 0, last, 3, "/baz/test.txt",
          new LogEntry(1, "Create directory: /foo", "A /foo", "A /foo/test.txt"),
          new LogEntry(2, "Rename: /foo to /bar", "D /foo", "A /bar", "A /bar/test.txt"),
          new LogEntry(3, "Modify file: /bar/test.txt", "M /bar/test.txt")
      );
    }
  }

  private void checkLog(@NotNull SVNRepository repo, long r1, long r2, @NotNull String path, @NotNull LogEntry... expecteds) throws SVNException {
    checkLogLimit(repo, r1, r2, 0, path, expecteds);
  }

  private void checkLogLimit(@NotNull SVNRepository repo, long r1, long r2, int limit, @NotNull String path, @NotNull LogEntry... expecteds) throws SVNException {
    final List<LogEntry> actual = new ArrayList<>();
    repo.log(new String[]{path}, r1, r2, true, false, limit, logEntry -> actual.add(new LogEntry(logEntry)));
    ArrayAsserts.assertArrayEquals(expecteds, actual.toArray(new LogEntry[actual.size()]));
  }
}
