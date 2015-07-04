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
import svnserver.repository.git.path.PathMatcher;

/**
 * Matches with any path.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class AlwaysMatcher implements PathMatcher {
  @NotNull
  public final static AlwaysMatcher INSTANCE = new AlwaysMatcher();

  private AlwaysMatcher() {
  }

  @Nullable
  @Override
  public PathMatcher createChild(@NotNull String name, boolean isDir) {
    return this;
  }

  @Override
  public boolean isMatch() {
    return true;
  }

  @Nullable
  @Override
  public String getSvnMaskGlobal() {
    return "*";
  }
}
