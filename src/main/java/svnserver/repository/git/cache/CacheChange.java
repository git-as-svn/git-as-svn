/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
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
    this.oldFile = oldFile != null ? oldFile.copy() : null;
    this.newFile = newFile != null ? newFile.copy() : null;
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
