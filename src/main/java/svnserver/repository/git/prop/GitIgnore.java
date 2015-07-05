/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNProperty;
import svnserver.repository.git.path.PathMatcher;
import svnserver.repository.git.path.Wildcard;

import java.util.*;
import java.util.regex.PatternSyntaxException;

/**
 * Parse and processing .gitignore.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
final class GitIgnore implements GitProperty {

  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitIgnore.class);

  @NotNull
  private final List<PathMatcher> matchers;
  // svn:global-ignores
  @NotNull
  private final String[] global;
  // svn:ignore
  @NotNull
  private final String[] local;

  /**
   * Parse and store .gitignore data (http://git-scm.com/docs/gitignore).
   * <p>
   * Important:
   * * An optional prefix "!" which negates the pattern is not supported.
   * * Mask trailing slash is not supported (/foo/bar/ works like /foo/bar).
   *
   * @param content Original file content.
   */
  public GitIgnore(@NotNull String content) {
    final List<String> localList = new ArrayList<>();
    final List<String> globalList = new ArrayList<>();
    matchers = new ArrayList<>();
    for (String rawLine : content.split("\n")) {
      final String line = trimLine(rawLine);
      if (line.isEmpty()) continue;
      try {
        final Wildcard wildcard = new Wildcard(line);
        if (wildcard.isSvnCompatible()) {
          processMatcher(localList, globalList, matchers, wildcard.getMatcher());
        }
      } catch (InvalidPatternException | PatternSyntaxException e) {
        log.warn("Found invalid git pattern: {}", line);
      }
    }
    local = localList.toArray(new String[localList.size()]);
    global = globalList.toArray(new String[globalList.size()]);
  }

  private GitIgnore(@NotNull List<String> local, @NotNull List<String> global, @NotNull List<PathMatcher> matchers) {
    this.local = local.toArray(new String[local.size()]);
    this.global = global.toArray(new String[global.size()]);
    this.matchers = matchers;
  }

  private static void processMatcher(@NotNull List<String> local, @NotNull List<String> global, @NotNull List<PathMatcher> matchers, @Nullable PathMatcher matcher) {
    if (matcher == null) {
      return;
    }
    final String maskGlobal = matcher.getSvnMaskGlobal();
    if (maskGlobal != null) {
      global.add(maskGlobal);
      return;
    }
    final String maskLocal = matcher.getSvnMaskLocal();
    if (maskLocal != null) {
      local.add(maskLocal);
      return;
    }
    matchers.add(matcher);
  }

  private String trimLine(@NotNull String line) {
    if (line.isEmpty() || line.startsWith("#") || line.startsWith("!") || line.startsWith("\\!")) return "";
    // Remove trailing spaces end escapes.
    int end = line.length();
    while (end > 0) {
      final char c = line.charAt(end - 1);
      if (c != ' ') {
        if ((end < line.length()) && (line.charAt(end - 1) == '\\')) {
          end++;
        }
        break;
      }
      end--;
    }
    return line.substring(0, end);
  }

  @Override
  public void apply(@NotNull Map<String, String> props) {
    if (global.length > 0) {
      props.compute(SVNProperty.INHERITABLE_IGNORES, (key, value) -> addIgnore(value, global));
    }
    if (local.length > 0) {
      props.compute(SVNProperty.IGNORE, (key, value) -> addIgnore(value, local));
    }
  }

  @Nullable
  @Override
  public String getFilterName() {
    return null;
  }

  private static String addIgnore(@Nullable String oldValue, @NotNull String[] ignores) {
    final Set<String> contains = new HashSet<>();
    final StringBuilder result = new StringBuilder();
    if (oldValue != null) {
      result.append(oldValue);
      contains.addAll(Arrays.asList(oldValue.split("\n")));
    }
    for (String ignore : ignores) {
      if (contains.add(ignore)) {
        result.append(ignore).append('\n');
      }
    }
    return result.toString();
  }

  @Nullable
  @Override
  public GitProperty createForChild(@NotNull String name, @NotNull FileMode fileMode) {
    if (matchers.isEmpty() || (fileMode.getObjectType() == Constants.OBJ_BLOB)) {
      return null;
    }
    final List<String> localList = new ArrayList<>();
    final List<String> globalList = new ArrayList<>();
    final List<PathMatcher> childMatchers = new ArrayList<>();
    for (PathMatcher matcher : matchers) {
      processMatcher(localList, globalList, childMatchers, matcher.createChild(name, true));
    }
    if (localList.isEmpty() && globalList.isEmpty() && childMatchers.isEmpty()) {
      return null;
    }
    return new GitIgnore(localList, globalList, childMatchers);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitIgnore gitIgnore = (GitIgnore) o;

    return Arrays.equals(global, gitIgnore.global)
        && Arrays.equals(local, gitIgnore.local)
        && matchers.equals(gitIgnore.matchers);
  }

  @Override
  public int hashCode() {
    int result = matchers.hashCode();
    result = 31 * result + Arrays.hashCode(global);
    result = 31 * result + Arrays.hashCode(local);
    return result;
  }
}
