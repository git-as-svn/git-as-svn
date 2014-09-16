/**
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
 * GIT reference mapping.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface RefMapping {
  /**
   * Convert git reference name to svn directory path.
   *
   * @param gitName Git reference name.
   * @return Svn directory path or null (if not matched).
   */
  @Nullable
  String gitToSvn(@NotNull String gitName);

  /**
   * Convert svn directory path to git reference name.
   *
   * @param svnPath Svn directory path.
   * @return Git reference name or null (if not matched).
   */
  @Nullable
  String svnToGit(@NotNull String svnPath);
}
