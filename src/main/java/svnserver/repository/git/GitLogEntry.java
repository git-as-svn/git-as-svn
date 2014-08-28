package svnserver.repository.git;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
  @Nullable
  private final GitFile oldEntry;
  @Nullable
  private final GitFile newEntry;

  public GitLogEntry(@Nullable GitFile oldEntry, @Nullable GitFile newEntry) {
    this.oldEntry = oldEntry;
    this.newEntry = newEntry;
  }

  @Override
  public char getChange() throws IOException {
    if (newEntry != null) {
      if (oldEntry == null) return SVNLogEntryPath.TYPE_ADDED;
      if (GitHelper.getKind(newEntry.getFileMode()) != GitHelper.getKind(oldEntry.getFileMode())) return SVNLogEntryPath.TYPE_REPLACED;
      return (isContentModified() || isPropertyModified()) ? SVNLogEntryPath.TYPE_MODIFIED : 0;
    } else {
      return SVNLogEntryPath.TYPE_DELETED;
    }
  }

  @Nullable
  public GitFile getOldEntry() {
    return oldEntry;
  }

  @Nullable
  public GitFile getNewEntry() {
    return newEntry;
  }

  @NotNull
  @Override
  public SVNNodeKind getKind() {
    if (newEntry != null) return GitHelper.getKind(newEntry.getFileMode());
    if (oldEntry != null) return GitHelper.getKind(oldEntry.getFileMode());
    throw new IllegalStateException();
  }

  @Override
  public boolean isContentModified() throws IOException {
    if (newEntry == null || newEntry.isDirectory())
      return false;

    if (oldEntry == null || oldEntry.isDirectory())
      return false;

    if (newEntry.isSymlink() == oldEntry.isSymlink()) {
      return !newEntry.getObjectId().equals(oldEntry.getObjectId());
    } else {
      return !newEntry.getMd5().equals(oldEntry.getMd5());
    }
  }

  @Override
  public boolean isPropertyModified() throws IOException {
    if ((newEntry == null) || (oldEntry == null)) return false;
    final Map<String, String> newProps = newEntry.getProperties(false);
    final Map<String, String> oldProps = oldEntry.getProperties(false);
    return (!newProps.equals(oldProps));
  }
}
