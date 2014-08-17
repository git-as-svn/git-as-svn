package svnserver.repository.git;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Git tree entry.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitTreeEntry {
  @NotNull
  private final FileMode fileMode;
  @NotNull
  private final GitObject<ObjectId> objectId;

  public GitTreeEntry(@NotNull FileMode fileMode, @NotNull GitObject<ObjectId> objectId) {
    this.fileMode = fileMode;
    this.objectId = objectId;
  }

  @NotNull
  public FileMode getFileMode() {
    return fileMode;
  }

  @NotNull
  public GitObject<ObjectId> getObjectId() {
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

  public GitObject<ObjectId> getTreeId(@NotNull GitRepository repository) throws IOException {
    if (fileMode == FileMode.TREE) return objectId;
    if (fileMode == FileMode.GITLINK) {
      GitObject<RevCommit> linked = repository.loadLinkedCommit(objectId.getObject());
      return new GitObject<>(linked.getRepo(), linked.getObject().getTree());
    }
    return null;
  }
}
