/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Git tree entry.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
final class GitTreeEntry implements Comparable<GitTreeEntry> {
  @NotNull
  private final FileMode fileMode;
  @NotNull
  private final GitObject<ObjectId> objectId;
  @NotNull
  private final String fileName;

  GitTreeEntry(@NotNull FileMode fileMode, @NotNull GitObject<ObjectId> objectId, @NotNull String fileName) {
    this.fileMode = fileMode;
    this.objectId = objectId;
    this.fileName = fileName;
  }

  GitTreeEntry(@NotNull Repository repo, @NotNull FileMode fileMode, @NotNull ObjectId objectId, @NotNull String fileName) {
    this(fileMode, new GitObject<>(repo, objectId), fileName);
  }

  @NotNull
  String getId() {
    return objectId.getObject().getName();
  }

  @NotNull
  FileMode getFileMode() {
    return fileMode;
  }

  @NotNull
  String getFileName() {
    return fileName;
  }

  @NotNull
  GitObject<ObjectId> getObjectId() {
    return objectId;
  }

  @Override
  public int compareTo(@NotNull GitTreeEntry peer) {
    int length1 = this.fileName.length();
    int length2 = peer.fileName.length();
    final int length = Math.min(length1, length2) + 1;
    for (int i = 0; i < length; i++) {
      final char c1;
      if (i < length1) {
        c1 = this.fileName.charAt(i);
      } else if ((i == length1) && (this.getFileMode() == FileMode.TREE)) {
        c1 = '/';
      } else {
        c1 = 0;
      }
      final char c2;
      if (i < length2) {
        c2 = peer.fileName.charAt(i);
      } else if ((i == length2) && (peer.getFileMode() == FileMode.TREE)) {
        c2 = '/';
      } else {
        c2 = 0;
      }
      if (c1 != c2) {
        return c1 - c2;
      }
    }
    return length1 - length2;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitTreeEntry that = (GitTreeEntry) o;

    return objectId.equals(that.objectId)
        && fileMode.equals(that.fileMode)
        && fileName.equals(that.fileName);
  }

  @Override
  public int hashCode() {
    int result = fileMode.hashCode();
    result = 31 * result + objectId.hashCode();
    result = 31 * result + fileName.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "GitTreeEntry{" +
        "fileMode=" + fileMode +
        ", objectId=" + objectId +
        ", fileName='" + fileName + '\'' +
        '}';
  }
}
