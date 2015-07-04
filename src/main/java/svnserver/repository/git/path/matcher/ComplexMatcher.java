/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.path.matcher;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.ignore.internal.IMatcher;
import org.eclipse.jgit.ignore.internal.PathMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.repository.git.path.NameMatcher;

/**
 * Simple matcher for regexp compare.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ComplexMatcher implements NameMatcher {
  @Nullable
  private final String svnMask;
  @NotNull
  private final IMatcher matcher;

  public ComplexMatcher(@NotNull String pattern, boolean dirOnly, boolean isSvnMask) throws InvalidPatternException {
    this.svnMask = isSvnMask ? pattern : null;
    this.matcher = PathMatcher.createPathMatcher(dirOnly ? pattern.substring(0, pattern.length() - 1) : pattern, null, dirOnly);
  }

  @Override
  public boolean isMatch(@NotNull String name, boolean isDir) {
    return matcher.matches(name, isDir);
  }

  @Override
  public boolean isRecursive() {
    return false;
  }

  @Nullable
  @Override
  public String getSvnMask() {
    return svnMask;
  }
}
