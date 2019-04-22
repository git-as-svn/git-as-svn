/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.layout;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Group some RefMapping classes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class RefMappingGroup implements RefMapping {
  @NotNull
  private final RefMapping[] mappings;

  public RefMappingGroup(@NotNull RefMapping... mappings) {
    this.mappings = mappings;
  }

  @Nullable
  @Override
  public String gitToSvn(@NotNull String gitName) {
    for (RefMapping mapping : mappings) {
      final String result = mapping.gitToSvn(gitName);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public String svnToGit(@NotNull String svnPath) {
    for (RefMapping mapping : mappings) {
      final String result = mapping.svnToGit(svnPath);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  /**
   * Get priority for svn directory path.
   *
   * @param svnPath Svn directory path.
   * @return Svn directory priority.
   */
  public int getPriority(@NotNull String svnPath) {
    for (int i = 0; i < mappings.length; ++i) {
      final String result = mappings[i].svnToGit(svnPath);
      if (result != null) {
        return i;
      }
    }
    return mappings.length;
  }
}
