/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.locks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.gitlfs.common.data.Lock;
import ru.bozaro.gitlfs.common.data.User;
import svnserver.StringHelper;
import svnserver.repository.git.GitBranch;

import java.util.Date;
import java.util.Objects;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class LockDesc {
  @NotNull
  public static final LockDesc[] emptyArray = {};

  @NotNull
  private final String path;
  @Nullable
  private final String branch;
  @NotNull
  private final String token;
  @Nullable
  private final String owner;
  @Nullable
  private final String comment;
  @Nullable
  private final String hash;
  private final long created;

  public LockDesc(@NotNull String path, @Nullable GitBranch branch, @Nullable String hash, @NotNull String token, @Nullable String owner, @Nullable String comment, long created) {
    this(path, branch == null ? null : branch.getShortBranchName(), hash, token, owner, comment, created);
  }

  public LockDesc(@NotNull String path, @Nullable String branch, @Nullable String hash, @NotNull String token, @Nullable String owner, @Nullable String comment, long created) {
    this.path = path.startsWith("/") ? path : "/" + path;
    this.branch = branch;
    this.hash = hash;
    this.token = token;
    this.owner = owner;
    this.comment = comment;
    this.created = created;
  }

  @NotNull
  public static LockDesc toLockDesc(@NotNull Lock lock) {
    final String path = "/" + lock.getPath();
    return new LockDesc(path, (String) null, null, lock.getId(), lock.getOwner() == null ? null : lock.getOwner().getName(), null, lock.getLockedAt().getTime());
  }

  @NotNull
  public static Lock toLock(@NotNull LockDesc lockDesc) {
    final String path = toLfsPath(lockDesc.getPath());
    return new Lock(lockDesc.getToken(), path, new Date(lockDesc.getCreated()), lockDesc.getOwner() == null ? null : new User(lockDesc.getOwner()));
  }

  @NotNull
  public static String toLfsPath(@Nullable String path) {
    if (path == null)
      return "";

    if (path.startsWith("/"))
      return path.substring(1);

    return path;
  }

  @NotNull
  public String getPath() {
    return path;
  }

  @NotNull
  public String getToken() {
    return token;
  }

  public long getCreated() {
    return created;
  }

  @Nullable
  public String getOwner() {
    return owner;
  }

  @Nullable
  public String getBranch() {
    return branch;
  }

  @Nullable
  public String getHash() {
    return hash;
  }

  @Nullable
  public String getComment() {
    return comment;
  }

  @NotNull
  public String getCreatedString() {
    return StringHelper.formatDate(created);
  }

  @Override
  public int hashCode() {
    return Objects.hash(path, branch, token, owner, comment, hash, created);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o)
      return true;

    if (!(o instanceof LockDesc))
      return false;

    final LockDesc lockDesc = (LockDesc) o;
    // TODO: https://github.com/go-gitea/gitea/issues/7871
    return // created == lockDesc.created &&
        path.equals(lockDesc.path) &&
        Objects.equals(branch, lockDesc.branch) &&
        token.equals(lockDesc.token) &&
        Objects.equals(owner, lockDesc.owner) &&
        Objects.equals(comment, lockDesc.comment) &&
        Objects.equals(hash, lockDesc.hash);
  }

  @Override
  public String toString() {
    return "LockDesc{" +
        "path='" + path + '\'' +
        ", branch='" + branch + '\'' +
        ", token='" + token + '\'' +
        ", owner='" + owner + '\'' +
        ", comment='" + comment + '\'' +
        ", hash='" + hash + '\'' +
        ", created=" + created +
        '}';
  }
}
