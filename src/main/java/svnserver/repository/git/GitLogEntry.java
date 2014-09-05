package svnserver.repository.git;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import svnserver.repository.VcsLogEntry;

import java.io.IOException;
import java.util.Map;

/**
 * Git modification type.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitLogEntry implements VcsLogEntry {
  @NotNull
  private final GitLogPair pair;
  @Nullable
  private final String copyPath;
  private final int copyRev;

  public GitLogEntry(int rev, @NotNull GitLogPair pair, @NotNull Map<String, String> renames) {
    this.pair = pair;
    this.copyPath = pair.getNewEntry() != null ? renames.get(pair.getNewEntry().getFullPath()) : null;
    this.copyRev = copyPath == null ? -1 : rev - 1;
  }

  @Override
  public char getChange() throws IOException, SVNException {
    if (pair.getNewEntry() == null)
      return SVNLogEntryPath.TYPE_DELETED;

    if (pair.getOldEntry() == null)
      return SVNLogEntryPath.TYPE_ADDED;

    if (pair.getNewEntry().getKind() != pair.getOldEntry().getKind())
      return SVNLogEntryPath.TYPE_REPLACED;

    return isContentModified() || isPropertyModified() ? SVNLogEntryPath.TYPE_MODIFIED : 0;
  }

  @NotNull
  @Override
  public SVNNodeKind getKind() {
    if (pair.getNewEntry() != null)
      return pair.getNewEntry().getKind();

    if (pair.getOldEntry() != null)
      return pair.getOldEntry().getKind();

    throw new IllegalStateException();
  }

  @Nullable
  @Override
  public String getCopyFromPath() {
    return copyPath;
  }

  @Override
  public int getCopyFromRev() {
    return copyRev;
  }

  @Override
  public boolean isContentModified() throws IOException, SVNException {
    return pair.isContentModified();
  }

  @Override
  public boolean isPropertyModified() throws IOException {
    return pair.isPropertyModified();
  }
}
