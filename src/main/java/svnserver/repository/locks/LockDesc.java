/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.locks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.StringHelper;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class LockDesc {

  @NotNull
  private final String path;
  @NotNull
  private final String token;
  @NotNull
  private final String owner;
  @Nullable
  private final String comment;
  @NotNull
  private final String hash;
  private final long created;

  public LockDesc(@NotNull String path, @NotNull String hash, @NotNull String token, @NotNull String owner, @Nullable String comment, long created) {
    this.path = path;
    this.hash = hash;
    this.token = token;
    this.owner = owner;
    this.comment = comment;
    this.created = created;
  }

  @NotNull
  public String getPath() {
    return path;
  }

  @NotNull
  public String getHash() {
    return hash;
  }

  @NotNull
  public String getToken() {
    return token;
  }

  @NotNull
  public String getOwner() {
    return owner;
  }

  @Nullable
  public String getComment() {
    return comment;
  }

  public long getCreated() {
    return created;
  }

  @NotNull
  public String getCreatedString() {
    return StringHelper.formatDate(created);
  }
}
