package svnserver.repository.git;

import org.jetbrains.annotations.Nullable;
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
}