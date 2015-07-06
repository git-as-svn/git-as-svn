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
 * Parse and processing .gitignore.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitFileProperty implements GitProperty {
  @NotNull
  private final PathMatcher matcher;
  @NotNull
  private final String property;
  @NotNull
  private final String value;

  /**
   * Set property to all matched file.
   *
   * @param matcher  File matcher.
   * @param property Property name.
   * @param value    Property value.
   */
  public GitFileProperty(@NotNull PathMatcher matcher, @NotNull String property, @NotNull String value) {
    this.matcher = matcher;
    this.property = property;
    this.value = value;
  }

  @Override
  public void apply(@NotNull Map<String, String> props) {
  }

  @Nullable
  @Override
  public String getFilterName() {
    return null;
  }

  @Nullable
  @Override
  public GitProperty createForChild(@NotNull String name, @NotNull FileMode fileMode) {
    final boolean isDir = fileMode.getObjectType() != Constants.OBJ_BLOB;
    final PathMatcher matcherChild = matcher.createChild(name, isDir);
    if (matcherChild != null) {
      if (isDir) {
        return new GitFileProperty(matcherChild, property, value);
      } else if (matcherChild.isMatch()) {
        return new GitProperty() {
          @Override
          public void apply(@NotNull Map<String, String> props) {
            props.put(property, value);
          }

          @Nullable
          @Override
          public String getFilterName() {
            return null;
          }

          @Nullable
          @Override
          public GitProperty createForChild(@NotNull String name, @NotNull FileMode mode) {
            return null;
          }
        };
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitFileProperty that = (GitFileProperty) o;

    return matcher.equals(that.matcher)
        && property.equals(that.property)
        && value.equals(that.value);
  }

  @Override
  public int hashCode() {
    int result = matcher.hashCode();
    result = 31 * result + property.hashCode();
    result = 31 * result + value.hashCode();
    return result;
  }
}
