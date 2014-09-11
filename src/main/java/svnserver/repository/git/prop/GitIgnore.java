package svnserver.repository.git.prop;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNProperty;

import java.io.IOException;
import java.util.*;

/**
 * Parse and processing .gitignore.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
final class GitIgnore implements GitProperty {
  @SuppressWarnings("UnusedDeclaration")
  public static final class Factory implements GitPropertyFactory {
    @NotNull
    @Override
    public String getFileName() {
      return ".gitignore";
    }

    @NotNull
    @Override
    public GitProperty create(@NotNull String content) throws IOException {
      return new GitIgnore(content);
    }
  }

  @NotNull
  private final List<Rule> rules;
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
    rules = new ArrayList<>();
    for (String rawLine : content.split("\n")) {
      String line = parseLine(rawLine);
      if (line.isEmpty()) continue;
      processLine(localList, globalList, rules, line);
    }
    local = localList.toArray(new String[localList.size()]);
    global = globalList.toArray(new String[globalList.size()]);
  }

  private GitIgnore(@NotNull List<String> local, @NotNull List<String> global, @NotNull List<Rule> rules) {
    this.local = local.toArray(new String[local.size()]);
    this.global = global.toArray(new String[global.size()]);
    this.rules = rules;
  }

  private static void processLine(@NotNull List<String> localList, @NotNull List<String> globalList, @NotNull List<Rule> rules, @NotNull String ruleLine) {
    String line = ruleLine;
    if (line.startsWith("/") && line.indexOf('/', 1) != -1) line = line.substring(1);
    if (line.startsWith("**/") && line.indexOf('/', 3) == -1) line = line.substring(3);
    // Remove unusefull prefix
    final int lastIndex = line.lastIndexOf('/');
    if (lastIndex == -1) {
      // simple mask in all dirs
      globalList.add(line);
      return;
    } else if (lastIndex == 0) {
      // simple mask in current dir
      localList.add(line.substring(1));
      return;
    }
    final int index = line.indexOf('/');
    rules.add(new Rule(line.substring(0, index), line.substring(index)));
  }

  @NotNull
  public static String parseLine(@NotNull String line) {
    if (line.isEmpty() || line.startsWith("#") || line.startsWith("!") || line.startsWith("\\!")) return "";
    // Remove trailing spaces end escapes.
    int end = line.length();
    while (end > 0) {
      if (line.charAt(end - 1) != ' ') {
        if ((end < line.length()) && (line.charAt(end - 1) == '\\')) {
          end++;
        }
        break;
      }
      end--;
    }
    String parsed = line.substring(0, end).replaceAll("\\\\", "");
    // Add leading "/"
    if (!parsed.startsWith("/") && parsed.contains("/")) {
      parsed = "/" + parsed;
    }
    // Remove trailing "/"
    if (parsed.endsWith("/")) {
      if (parsed.indexOf('/') == parsed.length() - 1) {
        // foo/ -> /foo
        parsed = "/" + parsed.substring(0, parsed.length() - 1);
      } else {
        // /foo/bar/ -> /foo/bar
        // foo/bar/ -> foo/bar
        parsed = parsed.substring(0, parsed.length() - 1);
      }
    }
    // Remove trailing "/**"
    while (parsed.endsWith("/**")) {
      parsed = parsed.substring(0, parsed.length() - 3);
    }
    // Remove leading "**/"
    while (parsed.startsWith("/**/**/")) {
      parsed = parsed.substring(3);
    }
    if (parsed.startsWith("/**/") && (parsed.indexOf('/', 4) == -1)) {
      parsed = parsed.substring(4);
    }
    // Remove leading "/"
    if (parsed.startsWith("/") && (parsed.indexOf('/', 1) != -1)) {
      parsed = parsed.substring(1);
    }
    // Return.
    return parsed;
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
    if (rules.isEmpty() || (fileMode.getObjectType() == Constants.OBJ_BLOB)) {
      return null;
    }
    final List<String> localList = new ArrayList<>();
    final List<String> globalList = new ArrayList<>();
    final List<Rule> childRules = new ArrayList<>();
    for (Rule rule : rules) {
      if (rule.mask.equals("**")) {
        childRules.add(rule);
        final int index = rule.rule.indexOf('/', 1);
        if (rule.rule.substring(1, index).equals(name)) {
          processLine(localList, globalList, childRules, rule.rule.substring(index));
        }
      }
      if (FilenameUtils.wildcardMatch(name, rule.mask, IOCase.SENSITIVE)) {
        processLine(localList, globalList, childRules, rule.rule);
      }
    }
    if (localList.isEmpty() && globalList.isEmpty() && childRules.isEmpty()) {
      return null;
    }
    return new GitIgnore(localList, globalList, childRules);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitIgnore gitIgnore = (GitIgnore) o;

    return Arrays.equals(global, gitIgnore.global)
        && Arrays.equals(local, gitIgnore.local)
        && rules.equals(gitIgnore.rules);
  }

  @Override
  public int hashCode() {
    int result = rules.hashCode();
    result = 31 * result + Arrays.hashCode(global);
    result = 31 * result + Arrays.hashCode(local);
    return result;
  }

  private final static class Rule {
    @NotNull
    private final String mask;
    @NotNull
    private final String rule;

    private Rule(@NotNull String mask, @NotNull String rule) {
      this.mask = mask;
      this.rule = rule;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Rule rule1 = (Rule) o;
      return mask.equals(rule1.mask)
          && rule.equals(rule1.rule);
    }

    @Override
    public int hashCode() {
      int result = mask.hashCode();
      result = 31 * result + rule.hashCode();
      return result;
    }
  }
}
