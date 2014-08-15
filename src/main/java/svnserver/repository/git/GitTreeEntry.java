package svnserver.repository.git;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Git tree entry.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitTreeEntry {
  @NotNull
  private final FileMode fileMode;
  @NotNull
  private final ObjectId objectId;

  public GitTreeEntry(@NotNull FileMode fileMode, @NotNull ObjectId objectId) {
    this.fileMode = fileMode;
    this.objectId = objectId;
  }

  @NotNull
  public FileMode getFileMode() {
    return fileMode;
  }

  @NotNull
  public ObjectId getObjectId() {
    return objectId;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitTreeEntry that = (GitTreeEntry) o;

    return fileMode.equals(that.fileMode)
        && objectId.equals(that.objectId);
  }

  @Override
  public int hashCode() {
    int result = fileMode.hashCode();
    result = 31 * result + objectId.hashCode();
    return result;
  }

  public boolean isSvnHidden() {
    return fileMode == FileMode.GITLINK;
  }
}
