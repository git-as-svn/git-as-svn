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
import org.tmatesoft.svn.core.SVNProperty;
import svnserver.repository.git.path.PathMatcher;

import java.util.Map;

/**
 * Parse and processing .gitignore.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitAutoProperty implements GitProperty {
  @NotNull
  private final static String MASK_SEPARATOR = " = ";
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
  public GitAutoProperty(@NotNull PathMatcher matcher, @NotNull String property, @NotNull String value) {
    this.matcher = matcher;
    this.property = property;
    this.value = value;
  }

  @Override
  public void apply(@NotNull Map<String, String> props) {
    final String mask = matcher.getSvnMaskGlobal();
    if (mask != null) {
      String autoprops = props.getOrDefault(SVNProperty.INHERITABLE_AUTO_PROPS, "");
      int beg = 0;
      while (true) {
        if (autoprops.substring(beg).startsWith(mask + MASK_SEPARATOR)) {
          int end = autoprops.indexOf('\n', beg + 1);
          if (end < 0) {
            end = autoprops.length();
          }
          autoprops = autoprops.substring(0, end)
              + "; " + property + "=" + value
              + autoprops.substring(end);
          break;
        }
        beg = autoprops.indexOf('\n', beg + 1);
        if (beg < 0) {
          autoprops = autoprops
              + mask
              + MASK_SEPARATOR
              + property + "=" + value + "\n";
          break;
        }
      }
      props.put(SVNProperty.INHERITABLE_AUTO_PROPS, autoprops);
    }
  }

  @Nullable
  @Override
  public String getFilterName() {
    return null;
  }

  @Nullable
  @Override
  public GitProperty createForChild(@NotNull String name, @NotNull FileMode fileMode) {
    if (fileMode.getObjectType() == Constants.OBJ_BLOB) {
      return null;
    }
    if (matcher.getSvnMaskGlobal() != null) {
      return null;
    }
    final PathMatcher matcherChild = matcher.createChild(name, true);
    if (matcherChild == null) {
      return null;
    }
    return new GitAutoProperty(matcherChild, property, value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitAutoProperty that = (GitAutoProperty) o;

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
