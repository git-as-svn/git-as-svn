/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.repository.git.path.PathMatcher;

import java.util.Map;

/**
 * Replace file filter.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitFilterProperty implements GitProperty {
  @NotNull
  private final PathMatcher matcher;
  @NotNull
  private final String filterName;

  /**
   * Set property to all matched file.
   *
   * @param matcher    File matcher.
   * @param filterName Filter name.
   */
  public GitFilterProperty(@NotNull PathMatcher matcher, @NotNull String filterName) {
    this.matcher = matcher;
    this.filterName = filterName;
  }

  @Override
  public void apply(@NotNull Map<String, String> props) {
  }

  @Nullable
  @Override
  public String getFilterName() {
    return matcher.isMatch() ? filterName : null;
  }

  @Nullable
  @Override
  public GitProperty createForChild(@NotNull String name, @NotNull FileMode fileMode) {
    final boolean isDir = fileMode.getObjectType() != Constants.OBJ_BLOB;
    final PathMatcher matcherChild = matcher.createChild(name, isDir);
    if ((matcherChild != null) && (isDir || matcherChild.isMatch())) {
      return new GitFilterProperty(matcherChild, filterName);
    }
    return null;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final GitFilterProperty that = (GitFilterProperty) o;

    return matcher.equals(that.matcher)
        && filterName.equals(that.filterName);
  }

  @Override
  public int hashCode() {
    int result = matcher.hashCode();
    result = 31 * result + filterName.hashCode();
    return result;
  }
}
