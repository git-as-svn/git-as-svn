package svnserver.repository.git.prop;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Parse and processing .gitattributes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitAttributes implements GitProperty {
  @NotNull
  private final static String EOL_PREFIX = "eol=";
  @NotNull
  private final static Rule[] emptyRules = {};
  @NotNull
  private final GitEolDir eolDir;

  /**
   * Parse and store .gitattribues data.
   *
   * @param content Original file content.
   */
  public GitAttributes(@NotNull String content) {
    this.eolDir = new GitEolDir(parseRules(content));
  }

  @NotNull
  private Rule[] parseRules(@NotNull String content) {
    final List<Rule> parsedRules = new ArrayList<>();
    for (String line : content.split("(?:#[^\n]*)?\n")) {
      final String[] tokens = line.trim().split("\\s+");
      final String eol = getEol(tokens);
      if (eol != null) {
        parsedRules.add(new Rule(tokens[0], eol));
      }
    }
    if (parsedRules.isEmpty()) return emptyRules;
    return parsedRules.toArray(new Rule[parsedRules.size()]);
  }

  @Nullable
  private String getEol(String[] tokens) {
    for (int i = 1; i < tokens.length; ++i) {
      final String token = tokens[i];
      if (token.startsWith(EOL_PREFIX)) {
        switch (token.substring(EOL_PREFIX.length())) {
          case "lf":
            return SVNProperty.EOL_STYLE_LF;
          case "native":
            return SVNProperty.EOL_STYLE_NATIVE;
          case "cr":
            return SVNProperty.EOL_STYLE_CR;
          case "crlf":
            return SVNProperty.EOL_STYLE_CRLF;
        }
      }
    }
    return null;
  }

  @Override
  public void apply(@NotNull Map<String, String> props) {
    if (eolDir.rules.length > 0) {
      final StringBuilder sb = new StringBuilder();
      for (Rule rule : eolDir.rules) {
        sb.append(rule.mask).append(" = ").append(SVNProperty.EOL_STYLE).append('=').append(rule.eol).append('\n');
      }
      props.put(SVNProperty.INHERITABLE_AUTO_PROPS, sb.toString());
    }
  }

  @Nullable
  @Override
  public GitProperty createForChild(@NotNull String name, @NotNull FileMode fileMode) {
    return eolDir.createForChild(name, fileMode);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitAttributes that = (GitAttributes) o;
    return eolDir.equals(that.eolDir);
  }

  @Override
  public int hashCode() {
    return eolDir.hashCode();
  }

  private final static class Rule {
    @NotNull
    private final String mask;
    @NotNull
    private final String eol;

    private Rule(@NotNull String mask, @NotNull String eol) {
      this.mask = mask;
      this.eol = eol;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Rule rule = (Rule) o;

      return eol.equals(rule.eol)
          && mask.equals(rule.mask);
    }

    @Override
    public int hashCode() {
      int result = mask.hashCode();
      result = 31 * result + eol.hashCode();
      return result;
    }
  }

  private static final class GitEolDir implements GitProperty {
    @NotNull
    private final Rule[] rules;

    public GitEolDir(@NotNull Rule[] rules) {
      this.rules = rules;
    }

    @Override
    public void apply(@NotNull Map<String, String> props) {
    }

    @Nullable
    @Override
    public GitProperty createForChild(@NotNull String name, @NotNull FileMode mode) {
      if (mode.getObjectType() == Constants.OBJ_BLOB) {
        for (Rule rule : rules) {
          if (FilenameUtils.wildcardMatch(name, rule.mask, IOCase.SENSITIVE)) {
            return new GitEolFile(rule.eol);
          }
        }
        return null;
      } else {
        return this;
      }
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      GitEolDir that = (GitEolDir) o;
      return Arrays.equals(rules, that.rules);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(rules);
    }
  }

  private static final class GitEolFile implements GitProperty {
    @NotNull
    private final String eol;

    private GitEolFile(@NotNull String eol) {
      this.eol = eol;
    }

    @Override
    public void apply(@NotNull Map<String, String> props) {
      props.put(SVNProperty.EOL_STYLE, eol);
    }

    @Nullable
    @Override
    public GitProperty createForChild(@NotNull String name, @NotNull FileMode fileMode) {
      return null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      GitEolFile that = (GitEolFile) o;

      return eol.equals(that.eol);
    }

    @Override
    public int hashCode() {
      return eol.hashCode();
    }
  }
}
