package svnserver.repository.git;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;

/**
 * Git tree entry.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitTreeEntry {
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
}
