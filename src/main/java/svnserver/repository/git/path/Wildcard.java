/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.path;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

/**
 * Git wildcard mask.
 * <p>
 * Pattern format: http://git-scm.com/docs/gitignore
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class Wildcard {
  public static final char PATH_SEPARATOR = '/';

  private final boolean negativeMask;
  //private final String[] tokens;

  public Wildcard(@NotNull String pattern) {
    negativeMask = pattern.startsWith("!");
    List<String> tokens = splitPattern(negativeMask ? pattern.substring(1) : pattern);
    normalizePattern(tokens);
  }

  /**
   * Split pattern with saving slashes.
   *
   * @param pattern Path pattern.
   * @return Path pattern items.
   */
  static List<String> splitPattern(@NotNull String pattern) {
    final List<String> result = new ArrayList<>(count(pattern, PATH_SEPARATOR) + 1);
    int start = 0;
    while (true) {
      int next = pattern.indexOf(PATH_SEPARATOR, start);
      if (next == -1) {
        if (start < pattern.length()) {
          result.add(pattern.substring(start));
        }
        break;
      }
      result.add(pattern.substring(start, next + 1));
      start = next + 1;
    }
    return result;
  }

  /**
   * Remove redundant pattern parts and make patterns more simple.
   *
   * @param tokens Original modifiable list.
   * @return Return tokens,
   */
  static List<String> normalizePattern(@NotNull List<String> tokens) {
    // Add "any path" prefix for simple mask
    if (tokens.size() == 1 && !tokens.get(0).endsWith("/")) {
      tokens.add(0, "**/");
    }
    ListIterator<String> iter = tokens.listIterator();
    String prev = null;
    while (iter.hasNext()) {
      String xxx;
      if (iter.hasPrevious()) {
        xxx = iter.previous();
        iter.next();
      } else {
        xxx = null;
      }
      if (Objects.equals(prev, xxx)) {
        int i = 0;
      }

      final String token = iter.next();
      if ("**/".equals(prev)) {
        if (token.equals("*/")) {
          iter.previous();
          iter.previous();
          iter.set(token);
          iter.next();
          iter.next();
          iter.set(prev);
          continue;
        }
        if (token.equals("*")) {
          iter.previous();
          iter.previous();
          iter.remove();
          if (iter.hasPrevious()) {
            prev = iter.previous();
            iter.next();
          } else {
            prev = null;
          }
          continue;
        }
      }
      if (token.equals("**")) {
        iter.remove();
        iter.previous();
        continue;
      }
      if (token.equals("**/")) {
        if ("**/".equals(prev)) {
          iter.remove();
        }
        prev = token;
        continue;
      }
      // Convert "**.test" to "**/" + "*.test"
      if (token.startsWith("**")) {
        iter.remove();
        iter.add("**/");
        iter.add(token.substring(1));
        iter.previous();
        iter.previous();
        continue;
      }
      prev = token;
    }
    return tokens;
  }

  static int count(String s, char c) {
    int start = 0;
    int count = 0;
    while (true) {
      start = s.indexOf(c, start);
      if (start == -1)
        break;
      count++;
      start++;
    }
    return count;
  }
}
