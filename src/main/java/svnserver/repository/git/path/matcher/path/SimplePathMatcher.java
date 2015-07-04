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

import java.util.Objects;

/**
 * Matcher for patterns without "**".
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SimplePathMatcher implements PathMatcher {
  @NotNull
  private final NameMatcher[] nameMatchers;
  private final int index;

  public SimplePathMatcher(@NotNull NameMatcher[] nameMatchers) {
    this(nameMatchers, 0);
  }

  private SimplePathMatcher(@NotNull NameMatcher[] nameMatchers, int index) {
    this.nameMatchers = nameMatchers;
    this.index = index;
  }

  @Nullable
  @Override
  public PathMatcher createChild(@NotNull String name, boolean isDir) {
    if (nameMatchers[index].isMatch(name, isDir)) {
      if (index + 1 == nameMatchers.length) {
        return AlwaysMatcher.INSTANCE;
      }
      return new SimplePathMatcher(nameMatchers, index + 1);
    }
    return null;
  }

  @Override
  public boolean isMatch() {
    return false;
  }

  @Nullable
  @Override
  public String getSvnMaskLocal() {
    if (index + 1 == nameMatchers.length) {
      return nameMatchers[index].getSvnMask();
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SimplePathMatcher that = (SimplePathMatcher) o;

    if (nameMatchers.length - index != that.nameMatchers.length - that.index) return false;
    for (int i = index; i < nameMatchers.length; ++i) {
      if (!Objects.equals(nameMatchers[i], that.nameMatchers[i + that.index - index])) return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = 0;
    for (int i = index; i < nameMatchers.length; ++i) {
      result = 31 * result + nameMatchers[i].hashCode();
    }
    return result;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    for (int i = index; i < nameMatchers.length; ++i) {
      sb.append(nameMatchers[i].toString());
    }
    return sb.toString();
  }
}
