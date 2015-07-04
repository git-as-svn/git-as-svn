/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.path.matcher.path;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.repository.git.path.NameMatcher;
import svnserver.repository.git.path.PathMatcher;

/**
 * Complex full-feature pattern matcher.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class FileMaskMatcher implements PathMatcher {
  @NotNull
  private final NameMatcher matcher;

  public FileMaskMatcher(@NotNull NameMatcher matcher) {
    this.matcher = matcher;
  }

  @Nullable
  @Override
  public PathMatcher createChild(@NotNull String name, boolean isDir) {
    if (matcher.isMatch(name, isDir)) {
      return AlwaysMatcher.INSTANCE;
    }
    return this;
  }

  @Override
  public boolean isMatch() {
    return false;
  }

  @Override
  @Nullable
  public String getSvnMaskGlobal() {
    return matcher.getSvnMask();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FileMaskMatcher that = (FileMaskMatcher) o;

    return matcher.equals(that.matcher);

  }

  @Override
  public int hashCode() {
    return matcher.hashCode();
  }
}
