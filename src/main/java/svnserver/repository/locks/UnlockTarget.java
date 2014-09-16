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

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class UnlockTarget {
  @NotNull
  private final String path;
  private final String token;

  public UnlockTarget(@NotNull String path, @Nullable String token) {
    this.path = path;
    this.token = token;
  }

  @NotNull
  public String getPath() {
    return path;
  }

  @Nullable
  public String getToken() {
    return token;
  }
}
