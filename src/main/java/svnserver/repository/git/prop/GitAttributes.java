package svnserver.repository.git.prop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parse and processing .gitattributes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitAttributes implements GitProperty {
  @NotNull
  private final static String EOL_PREFIX = "eol=";
  @NotNull
  private final static Rule[] emptyRules = {};
  @NotNull
  private final Rule[] rules;

  /**
   * Parse and store .gitattribues data.
   *
   * @param content Original file content.
   */
  public GitAttributes(@NotNull String content) {
    this.rules = parseRules(content);
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
        return token.substring(EOL_PREFIX.length());
      }
    }
    return null;
  }

  @Override
  public void apply(@NotNull Map<String, String> props) {
    if (rules.length > 0) {
      final StringBuilder sb = new StringBuilder();
      for (Rule rule : rules) {
        sb.append(rule.mask).append(" = ").append(SVNProperty.EOL_STYLE).append('=').append(rule.eol).append('\n');
      }
      props.put(SVNProperty.INHERITABLE_AUTO_PROPS, sb.toString());
    }
  }

  @Override
  public void applyOnChild(@NotNull String path, @NotNull Map<String, String> props) {
    // todo:
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
  }

}
