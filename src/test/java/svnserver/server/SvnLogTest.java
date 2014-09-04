package svnserver.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.Test;
import org.testng.internal.junit.ArrayAsserts;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
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
      this(logEntry.getRevision(), logEntry.getMessage(), logEntry.getChangedPaths().keySet());
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
   * Check commit .gitattributes.
   *
   * @throws Exception
   */
  @Test(timeOut = 60 * 1000)
  public void simpleLog() throws Exception {
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
          new LogEntry(4, "Create file: /foo/foo.txt", "/foo/foo.txt"),
          new LogEntry(3, "Modify file: /foo/bar.txt", "/foo/bar.txt"),
          new LogEntry(2, "Create directory: /foo", "/foo", "/foo/bar.txt"),
          new LogEntry(1, "Create file: /foo.txt", "/foo.txt"),
          new LogEntry(0, null)
      );

      // svn log from root
      checkLog(repo, last, 0, "/foo",
          new LogEntry(4, "Create file: /foo/foo.txt", "/foo/foo.txt"),
          new LogEntry(3, "Modify file: /foo/bar.txt", "/foo/bar.txt"),
          new LogEntry(2, "Create directory: /foo", "/foo", "/foo/bar.txt")
      );

      // svn log from root
      checkLog(repo, last, 0, "/foo/bar.txt",
          new LogEntry(3, "Modify file: /foo/bar.txt", "/foo/bar.txt"),
          new LogEntry(2, "Create directory: /foo", "/foo", "/foo/bar.txt")
      );

      // svn empty log
      checkLog(repo, 0, 0, "/",
          new LogEntry(0, null)
      );
    }
  }

  private void checkLog(@NotNull SVNRepository repo, long r1, long r2, @NotNull String path, @NotNull LogEntry... expecteds) throws SVNException {
    final List<LogEntry> actual = new ArrayList<>();
    repo.log(new String[]{path}, r1, r2, true, false, logEntry -> actual.add(new LogEntry(logEntry)));
    ArrayAsserts.assertArrayEquals(expecteds, actual.toArray(new LogEntry[actual.size()]));
  }
}
