/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import java.io.IOException;
import java.util.*;

/**
 * Parse and processing .gitattributes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
final class GitAttributes implements GitProperty {
  @SuppressWarnings("UnusedDeclaration")
  public static final class Factory implements GitPropertyFactory {
    @NotNull
    @Override
    public String getFileName() {
      return ".gitattributes";
    }

    @NotNull
    @Override
    public GitProperty create(@NotNull String content) throws IOException {
      return new GitAttributes(content);
    }
  }

  @NotNull
  private static final String EOL_PREFIX = "eol=";
  @NotNull
  private static final Rule[] emptyRules = {};
  @NotNull
  private final GitRuleDir eolDir;

  private static final class Rule {
    @NotNull
    private final String mask;
    @NotNull
    private final String prop;
    @NotNull
    private final String value;

    protected Rule(@NotNull String mask, @NotNull String prop, @NotNull String value) {
      this.mask = mask;
      this.prop = prop;
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final Rule rule = (Rule) o;
      return mask.equals(rule.mask)
          && prop.equals(rule.prop)
          && value.equals(rule.value);
    }

    @Override
    public int hashCode() {
      int result = mask.hashCode();
      result = 31 * result + prop.hashCode();
      result = 31 * result + value.hashCode();
      return result;
    }
  }

  /**
   * Parse and store .gitattribues data.
   *
   * @param content Original file content.
   */
  public GitAttributes(@NotNull String content) {
    this.eolDir = new GitRuleDir(parseRules(content));
  }

  @NotNull
  private Rule[] parseRules(@NotNull String content) {
    final List<Rule> parsedRules = new ArrayList<>();
    for (String line : content.split("(?:#[^\n]*)?\n")) {
      final String[] tokens = line.trim().split("\\s+");
      final String mimeType = getMimeType(tokens);
      if (mimeType != null) {
        parsedRules.add(new Rule(tokens[0], SVNProperty.MIME_TYPE, mimeType));
      }
      final String eol = getEol(tokens);
      if (eol != null) {
        parsedRules.add(new Rule(tokens[0], SVNProperty.EOL_STYLE, eol));
      }
    }
    if (parsedRules.isEmpty()) return emptyRules;
    return parsedRules.toArray(new Rule[parsedRules.size()]);
  }

  @Nullable
  private String getMimeType(String[] tokens) {
    for (int i = 1; i < tokens.length; ++i) {
      String token = tokens[i];
      if (token.startsWith("binary")) {
        return SVNFileUtil.BINARY_MIME_TYPE;
      }
    }
    return null;
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
      final Map<String, String> autoprops = new LinkedHashMap<>();
      for (Rule rule : eolDir.rules) {
        autoprops.compute(rule.mask, (key, value) -> (value == null ? "" : value + "; ") + rule.prop + "=" + rule.value);
      }
      final StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, String> entry : autoprops.entrySet()) {
        sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
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

  private static final class GitRuleDir implements GitProperty {
    @NotNull
    private final Rule[] rules;

    public GitRuleDir(@NotNull Rule[] rules) {
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
            return new GitRuleFile(rule);
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

      GitRuleDir that = (GitRuleDir) o;
      return Arrays.equals(rules, that.rules);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(rules);
    }
  }

  private static final class GitRuleFile implements GitProperty {
    @NotNull
    private final Rule rule;

    private GitRuleFile(@NotNull Rule rule) {
      this.rule = rule;
    }

    @Override
    public void apply(@NotNull Map<String, String> props) {
      props.put(rule.prop, rule.value);
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

      final GitRuleFile that = (GitRuleFile) o;
      return rule.equals(that.rule);
    }

    @Override
    public int hashCode() {
      return rule.hashCode();
    }
  }
}
