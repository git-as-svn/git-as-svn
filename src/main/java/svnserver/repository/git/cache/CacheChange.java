package svnserver.repository.git.cache;

import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.repository.git.GitFile;
import svnserver.repository.git.GitLogPair;
import svnserver.repository.git.GitObject;

/**
 * Change file/directory information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class CacheChange {
  @Nullable
  private final ObjectId oldFile;
  @Nullable
  private final ObjectId newFile;

  protected CacheChange() {
    oldFile = null;
    newFile = null;
  }

  public CacheChange(@NotNull GitLogPair logPair) {
    this(getFileId(logPair.getOldEntry()), getFileId(logPair.getNewEntry()));
  }

  public CacheChange(@Nullable ObjectId oldFile, @Nullable ObjectId newFile) {
    this.oldFile = oldFile;
    this.newFile = newFile;
  }

  @Nullable
  public ObjectId getOldFile() {
    return oldFile;
  }

  @Nullable
  public ObjectId getNewFile() {
    return newFile;
  }

  @Nullable
  private static ObjectId getFileId(@Nullable GitFile gitFile) {
    final GitObject<ObjectId> gitObject = gitFile == null ? null : gitFile.getObjectId();
    return gitObject == null ? null : gitObject.getObject();
  }
}
