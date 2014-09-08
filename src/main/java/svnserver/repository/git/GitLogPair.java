package svnserver.repository.git;

import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * Git modification type.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitLogPair {
  @Nullable
  private final GitFile oldEntry;
  @Nullable
  private final GitFile newEntry;

  public GitLogPair(@Nullable GitFile oldEntry, @Nullable GitFile newEntry) {
    this.oldEntry = oldEntry;
    this.newEntry = newEntry;
  }

  @Nullable
  public GitFile getOldEntry() {
    return oldEntry;
  }

  @Nullable
  public GitFile getNewEntry() {
    return newEntry;
  }

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

  public boolean isPropertyModified() throws IOException {
    if ((newEntry == null) || (oldEntry == null)) return false;
    final Map<String, String> newProps = newEntry.getProperties(false);
    final Map<String, String> oldProps = oldEntry.getProperties(false);
    return (!newProps.equals(oldProps));
  }
}