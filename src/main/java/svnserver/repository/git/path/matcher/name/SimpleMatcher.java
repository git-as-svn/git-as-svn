/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.path.matcher.name;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.repository.git.path.NameMatcher;

/**
 * Simple matcher for mask with only one asterisk.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SimpleMatcher implements NameMatcher {
  @NotNull
  private final String prefix;
  @NotNull
  private final String suffix;
  private final boolean dirOnly;

  public SimpleMatcher(@NotNull String prefix, @NotNull String suffix, boolean dirOnly) {
    this.prefix = prefix;
    this.suffix = suffix;
    this.dirOnly = dirOnly;
  }

  @Override
  public boolean isMatch(@NotNull String name, boolean isDir) {
    return (!dirOnly || isDir) && (name.length() >= prefix.length() + suffix.length()) && name.startsWith(prefix) && name.endsWith(suffix);
  }

  @Override
  public boolean isRecursive() {
    return false;
  }

  @Nullable
  @Override
  public String getSvnMask() {
    return prefix + "*" + suffix;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final SimpleMatcher that = (SimpleMatcher) o;

    return (dirOnly == that.dirOnly)
        && (prefix.equals(that.prefix))
        && suffix.equals(that.suffix);
  }

  @Override
  public int hashCode() {
    int result = prefix.hashCode();
    result = 31 * result + suffix.hashCode();
    result = 31 * result + (dirOnly ? 1 : 0);
    return result;
  }

  @Override
  @NotNull
  public String toString() {
    return prefix + "*" + suffix + (dirOnly ? "/" : "");
  }
}
