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
import svnserver.repository.git.path.NameMatcher;
import svnserver.repository.git.path.PathMatcher;

import java.util.Arrays;
import java.util.Objects;

/**
 * Complex full-feature pattern matcher.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class RecursivePathMatcher implements PathMatcher {
  @NotNull
  private final static int[] START_ARRAY = {0};
  @NotNull
  private final int[] indexes;
  @NotNull
  private final NameMatcher[] nameMatchers;

  public RecursivePathMatcher(@NotNull NameMatcher[] nameMatchers) {
    this(nameMatchers, START_ARRAY);
  }

  private RecursivePathMatcher(@NotNull NameMatcher[] nameMatchers, @NotNull int[] indexes) {
    this.nameMatchers = nameMatchers;
    this.indexes = indexes;
  }

  @Nullable
  @Override
  public PathMatcher createChild(@NotNull String name, boolean isDir) {
    final int[] childs = new int[indexes.length * 2];
    boolean changed = false;
    int count = 0;
    for (int index : indexes) {
      if (nameMatchers[index].isMatch(name, isDir)) {
        if (nameMatchers[index].isRecursive()) {
          childs[count++] = index;
          if (index + 1 < nameMatchers.length && nameMatchers[index + 1].isMatch(name, isDir)) {
            if (index + 2 == nameMatchers.length) {
              return AlwaysMatcher.INSTANCE;
            }
            childs[count++] = index + 2;
            changed = true;
          }
        } else {
          if (index + 1 == nameMatchers.length) {
            return AlwaysMatcher.INSTANCE;
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
    return count == 0 ? null : new RecursivePathMatcher(nameMatchers, Arrays.copyOf(childs, count));
  }

  @Override
  public boolean isMatch() {
    return false;
  }

  @Override
  @Nullable
  public String getSvnMaskGlobal() {
    for (int index : indexes) {
      if (index + 2 == nameMatchers.length) {
        if (nameMatchers[index].isRecursive()) {
          return nameMatchers[index + 1].getSvnMask();
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public String getSvnMaskLocal() {
    for (int index : indexes) {
      if (index + 1 == nameMatchers.length) {
        return nameMatchers[index].getSvnMask();
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RecursivePathMatcher that = (RecursivePathMatcher) o;

    if (indexes.length != that.indexes.length) return false;
    final int offset = indexes[0];
    final int thatOffset = that.indexes[0];
    if (nameMatchers.length - offset != that.nameMatchers.length - thatOffset) return false;

    final int shift = thatOffset - offset;
    for (int i = offset; i < indexes.length; ++i) {
      if (indexes[i] != that.indexes[i + shift]) {
        return false;
      }
    }
    for (int i = offset; i < nameMatchers.length; ++i) {
      if (!Objects.equals(nameMatchers[i], that.nameMatchers[i + shift])) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    int offset = indexes[0];
    int result = 0;
    for (int index : indexes) {
      result = 31 * (index - offset);
      assert (offset <= index);
    }
    for (int i = offset; i < nameMatchers.length; ++i) {
      result = 31 * result + nameMatchers[i].hashCode();
    }
    return result;
  }
}
