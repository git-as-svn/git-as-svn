/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.path;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.jetbrains.annotations.NotNull;
import svnserver.repository.git.path.matcher.name.ComplexMatcher;
import svnserver.repository.git.path.matcher.name.EqualsMatcher;
import svnserver.repository.git.path.matcher.name.RecursiveMatcher;
import svnserver.repository.git.path.matcher.name.SimpleMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Git wildcard mask.
 * <p>
 * Pattern format: http://git-scm.com/docs/gitignore
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
final class WildcardHelper {
  private static final char PATH_SEPARATOR = '/';

  @NotNull
  static NameMatcher nameMatcher(@NotNull String mask) throws InvalidPatternException {
    if (mask.equals("**/")) {
      return RecursiveMatcher.INSTANCE;
    }
    final boolean dirOnly = mask.endsWith("/");
    final String nameMask = tryRemoveBackslashes(dirOnly ? mask.substring(0, mask.length() - 1) : mask);
    if ((nameMask.indexOf('[') < 0) && (nameMask.indexOf(']') < 0) && (nameMask.indexOf('\\') < 0)) {
      // Subversion compatible mask.
      if (nameMask.indexOf('?') < 0) {
        int asterisk = nameMask.indexOf('*');
        if (asterisk < 0) {
          return new EqualsMatcher(nameMask, dirOnly);
        } else if (mask.indexOf('*', asterisk + 1) < 0) {
          return new SimpleMatcher(nameMask.substring(0, asterisk), nameMask.substring(asterisk + 1), dirOnly);
        }
      }
      return new ComplexMatcher(nameMask, dirOnly, true);
    } else {
      return new ComplexMatcher(nameMask, dirOnly, false);
    }
  }

  @NotNull
  static String tryRemoveBackslashes(@NotNull String pattern) {
    final StringBuilder result = new StringBuilder(pattern.length());
    int start = 0;
    while (true) {
      int next = pattern.indexOf('\\', start);
      if (next == -1) {
        if (start < pattern.length()) {
          result.append(pattern, start, pattern.length());
        }
        break;
      }
      if (next == pattern.length() - 1) {
        // Return original string.
        return pattern;
      }
      switch (pattern.charAt(next + 1)) {
        case ' ':
        case '#':
        case '!':
          result.append(pattern, start, next);
          start = next + 1;
          break;
        default:
          return pattern;
      }
    }
    return result.toString();
  }

  /**
   * Split pattern with saving slashes.
   *
   * @param pattern Path pattern.
   * @return Path pattern items.
   */
  @NotNull
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
  @NotNull
  static List<String> normalizePattern(@NotNull List<String> tokens) {
    // By default without slashes using mask for files in all subdirectories
    if (tokens.size() == 1 && !tokens.get(0).contains("/")) {
      tokens.add(0, "**/");
    }
    // Normalized pattern always starts with "/"
    if (tokens.size() == 0 || !tokens.get(0).equals("/")) {
      tokens.add(0, "/");
    }
    // Replace:
    //  * "**/*/" to "*/**/"
    //  * "**/**/" to "**/"
    //  * "**.foo" to "**/*.foo"
    int index = 1;
    while (index < tokens.size()) {
      final String thisToken = tokens.get(index);
      final String prevToken = tokens.get(index - 1);
      if (thisToken.equals("/")) {
        tokens.remove(index);
        continue;
      }
      if (thisToken.equals("**/") && prevToken.equals("**/")) {
        tokens.remove(index);
        continue;
      }
      if ((!thisToken.equals("**/")) && thisToken.startsWith("**")) {
        tokens.add(index, "**/");
        tokens.set(index + 1, thisToken.substring(1));
        continue;
      }
      if (thisToken.equals("*/") && prevToken.equals("**/")) {
        tokens.set(index - 1, "*/");
        tokens.set(index, "**/");
        index--;
        continue;
      }
      index++;
    }
    // Remove tailing "**/" and "*"
    while (!tokens.isEmpty()) {
      final String token = tokens.get(tokens.size() - 1);
      if (token.equals("**/") || token.equals("*")) {
        tokens.remove(tokens.size() - 1);
      } else {
        break;
      }
    }
    return tokens;
  }

  public static int count(@NotNull String s, char c) {
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
