/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.path;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.jetbrains.annotations.NotNull;
import svnserver.repository.git.path.matcher.path.AlwaysMatcher;
import svnserver.repository.git.path.matcher.path.FileMaskMatcher;
import svnserver.repository.git.path.matcher.path.RecursivePathMatcher;
import svnserver.repository.git.path.matcher.path.SimplePathMatcher;

import java.util.List;

/**
 * Git wildcard mask.
 * <p>
 * Pattern format: http://git-scm.com/docs/gitignore
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class Wildcard {
  @NotNull
  private final NameMatcher[] nameMatchers;
  @NotNull
  private final PathMatcher matcher;

  public Wildcard(@NotNull String pattern) throws InvalidPatternException {
    nameMatchers = createNameMatchers(pattern);
    if (nameMatchers.length > 0) {
      if (hasRecursive(nameMatchers)) {
        if (nameMatchers.length == 2 && nameMatchers[0].isRecursive() && !nameMatchers[1].isRecursive()) {
          matcher = new FileMaskMatcher(nameMatchers[1]);
        } else {
          matcher = new RecursivePathMatcher(nameMatchers);
        }
      } else {
        matcher = new SimplePathMatcher(nameMatchers);
      }
    } else {
      matcher = AlwaysMatcher.INSTANCE;
    }
  }

  public boolean isSvnCompatible() {
    return nameMatchers[nameMatchers.length - 1].getSvnMask() != null;
  }

  @NotNull
  public PathMatcher getMatcher() {
    return matcher;
  }

  private static boolean hasRecursive(@NotNull NameMatcher[] nameMatchers) {
    for (NameMatcher matcher : nameMatchers) {
      if (matcher.isRecursive()) {
        return true;
      }
    }
    return false;
  }

  private static NameMatcher[] createNameMatchers(@NotNull String pattern) throws InvalidPatternException {
    final List<String> tokens = WildcardHelper.splitPattern(pattern);
    WildcardHelper.normalizePattern(tokens);
    final NameMatcher[] result = new NameMatcher[tokens.size() - 1];
    for (int i = 0; i < result.length; ++i) {
      result[i] = WildcardHelper.nameMatcher(tokens.get(i + 1));
    }
    return result;
  }

}
