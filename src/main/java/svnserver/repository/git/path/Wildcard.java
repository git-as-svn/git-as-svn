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
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Git wildcard mask.
 * <p>
 * Pattern format: http://git-scm.com/docs/gitignore
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class Wildcard {
  private final boolean negativeMask;
  @NotNull
  private final NameMatcher[] nameMatchers;
  @NotNull
  private final PathMatcher matcher;

  public Wildcard(@NotNull String pattern) throws InvalidPatternException {
    negativeMask = pattern.startsWith("!");
    nameMatchers = createNameMatchers(negativeMask ? pattern.substring(1) : pattern);
    if (nameMatchers.length > 0) {
      matcher = hasRecursive(nameMatchers) ? new RecursivePathMatcher(0) : new SimplePathMatcher(0);
    } else {
      matcher = new AlwaysMatcher();
    }
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

  private static boolean isNameMatch(@NotNull NameMatcher matcher, @NotNull String name) {
    boolean isDir = name.endsWith("/");
    return matcher.isMatch(isDir ? name.substring(0, name.length() - 1) : name, isDir);
  }

  private class AlwaysMatcher implements PathMatcher {

    @Nullable
    @Override
    public PathMatcher createChild(@NotNull String name) {
      return this;
    }

    @Override
    public boolean isMatch() {
      return true;
    }
  }

  private class RecursivePathMatcher implements PathMatcher {
    private final int[] indexes;

    public RecursivePathMatcher(int... indexes) {
      this.indexes = indexes;
    }

    @Nullable
    @Override
    public PathMatcher createChild(@NotNull String name) {
      final int[] childs = new int[indexes.length * 2];
      boolean changed = false;
      int count = 0;
      for (int index : indexes) {
        if (isNameMatch(nameMatchers[index], name)) {
          if (nameMatchers[index].isRecursive()) {
            childs[count++] = index;
            if (index + 1 < nameMatchers.length && isNameMatch(nameMatchers[index + 1], name)) {
              if (index + 2 == nameMatchers.length) {
                return new AlwaysMatcher();
              }
              childs[count++] = index + 2;
              changed = true;
            }
          } else {
            if (index + 1 == nameMatchers.length) {
              return new AlwaysMatcher();
            }
            childs[count++] = index + 1;
            changed = true;
          }
        } else {
          changed = true;
        }
      }
      if (!changed) {
        return this;
      }
      return count == 0 ? null : new RecursivePathMatcher(Arrays.copyOf(childs, count));
    }

    @Override
    public boolean isMatch() {
      return false;
    }
  }

  private class SimplePathMatcher implements PathMatcher {
    private final int index;

    public SimplePathMatcher(int index) {
      this.index = index;
    }

    @Nullable
    @Override
    public PathMatcher createChild(@NotNull String name) {
      if (isNameMatch(nameMatchers[index], name)) {
        if (index + 1 == nameMatchers.length) {
          return new AlwaysMatcher();
        }
        return new SimplePathMatcher(index + 1);
      }
      return null;
    }

    @Override
    public boolean isMatch() {
      return false;
    }
  }
}
