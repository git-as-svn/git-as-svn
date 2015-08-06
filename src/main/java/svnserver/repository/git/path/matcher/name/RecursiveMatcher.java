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
 * Recursive directory matcher like "**".
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class RecursiveMatcher implements NameMatcher {
  public static final RecursiveMatcher INSTANCE = new RecursiveMatcher();

  private RecursiveMatcher() {
  }

  @Override
  public boolean isMatch(@NotNull String name, boolean isDir) {
    return isDir;
  }

  @Nullable
  @Override
  public String getSvnMask() {
    return null;
  }

  @Override
  public boolean isRecursive() {
    return true;
  }

  @Override
  @NotNull
  public String toString() {
    return "**/";
  }
}
