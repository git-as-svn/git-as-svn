/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop;

import org.eclipse.jgit.lib.FileMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

/**
 * Interface for mapping git file to subversion attributes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface GitProperty {
  GitProperty[] emptyArray = {};

  /**
   * Update file properties on element.
   *
   * @param props Properties.
   */
  void apply(@NotNull Map<String, String> props);

  /**
   * Create GitProperty for child element.
   *
   * @param name Child node name.
   * @param mode Child node type.
   * @return Child property modifier or null, if this property is not affected for childs.
   */
  @Nullable
  GitProperty createForChild(@NotNull String name, @NotNull FileMode mode);

  /**
   * Get overrided filter name.
   *
   * @return Filter name.
   */
  @Nullable
  String getFilterName();

  @NotNull
  static GitProperty[] joinProperties(@NotNull GitProperty[] parentProps, @NotNull String entryName, @NotNull FileMode fileMode, @NotNull GitProperty[] entryProps) {
    if (parentProps.length == 0) {
      return entryProps;
    }
    final GitProperty[] joined = new GitProperty[parentProps.length + entryProps.length];
    int index = 0;
    for (GitProperty parentProp : parentProps) {
      final GitProperty prop = parentProp.createForChild(entryName, fileMode);
      if (prop != null) {
        joined[index] = prop;
        index++;
      }
    }
    System.arraycopy(entryProps, 0, joined, index, entryProps.length);
    return index == parentProps.length ? joined : Arrays.copyOf(joined, index + entryProps.length);
  }
}
