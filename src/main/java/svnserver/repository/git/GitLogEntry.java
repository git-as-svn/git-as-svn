package svnserver.repository.git;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import svnserver.repository.VcsLogEntry;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

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
  @Nullable
  private final String copyPath;
  private final int copyRev;

  public GitLogEntry(int rev, @Nullable GitFile oldEntry, @Nullable GitFile newEntry, @NotNull Map<String, String> renames) {
    this.oldEntry = oldEntry;
    this.newEntry = newEntry;
    this.copyPath = newEntry != null ? renames.get(newEntry.getFullPath()) : null;
    this.copyRev = copyPath == null ? -1 : rev - 1;
  }

  @Override
  public char getChange() throws IOException, SVNException {
    if (newEntry == null)
      return SVNLogEntryPath.TYPE_DELETED;

    if (oldEntry == null)
      return SVNLogEntryPath.TYPE_ADDED;

    if (newEntry.getKind() != oldEntry.getKind())
      return SVNLogEntryPath.TYPE_REPLACED;

    return isContentModified() || isPropertyModified() ? SVNLogEntryPath.TYPE_MODIFIED : 0;
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
    if (newEntry != null)
      return newEntry.getKind();

    if (oldEntry != null)
      return oldEntry.getKind();

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
    if (newEntry == null || newEntry.isDirectory())
      return false;

    if (oldEntry == null || oldEntry.isDirectory())
      return false;

    if (newEntry.isSymlink() == oldEntry.isSymlink()) {
      return !Objects.equals(newEntry.getObjectId(), oldEntry.getObjectId());
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
