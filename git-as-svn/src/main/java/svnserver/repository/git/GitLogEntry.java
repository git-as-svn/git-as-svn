package svnserver.repository.git;

import org.eclipse.jgit.lib.FileMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import svnserver.repository.VcsLogEntry;

/**
 * Git modification type.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitLogEntry implements VcsLogEntry {
  @Nullable
  private final GitTreeEntry oldEntry;
  @Nullable
  private final GitTreeEntry newEntry;

  public GitLogEntry(@Nullable GitTreeEntry oldEntry, @Nullable GitTreeEntry newEntry) {
    this.oldEntry = oldEntry;
    this.newEntry = newEntry;
  }

  @Override
  public char getChange() {
    if (newEntry != null) {
      if (oldEntry == null) return SVNLogEntryPath.TYPE_ADDED;
      if (GitHelper.getKind(newEntry.getFileMode()) != GitHelper.getKind(oldEntry.getFileMode())) return SVNLogEntryPath.TYPE_REPLACED;
      return newEntry.getFileMode() != FileMode.TREE ? SVNLogEntryPath.TYPE_MODIFIED : 0;
    } else {
      return SVNLogEntryPath.TYPE_DELETED;
    }
  }

  @NotNull
  @Override
  public SVNNodeKind getKind() {
    if (newEntry != null) return GitHelper.getKind(newEntry.getFileMode());
    if (oldEntry != null) return GitHelper.getKind(oldEntry.getFileMode());
    throw new IllegalStateException();
  }

  @Override
  public boolean isContentModified() {
    return (newEntry == null) || (oldEntry == null)
        || (!newEntry.getObjectId().equals(oldEntry.getObjectId()));
  }

  @Override
  public boolean isPropertyModified() {
    return (newEntry != null) && (oldEntry != null)
        && (newEntry.getFileMode().equals(oldEntry.getFileMode()));
  }
}