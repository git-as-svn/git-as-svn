package svnserver.repository.git.prop;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNProperty;

import java.util.*;

/**
 * Parse and processing .gitattributes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitAttributes implements GitProperty {
  @NotNull
  private static final String MIME_BINARY = "application/octet-stream";
  @NotNull
  private final static String EOL_PREFIX = "eol=";
  @NotNull
  private final static EolRule[] emptyRules = {};
  @NotNull
  private final GitRuleDir eolDir;

  private enum MimeType {
    BINARY,
    UNKNOWN, TEXT
  }

  private static abstract class Rule {
    @NotNull
    private final String mask;

    protected Rule(@NotNull String mask) {
      this.mask = mask;
    }

    @NotNull
    public String getMask() {
      return mask;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Rule rule = (Rule) o;
      return mask.equals(rule.mask);
    }

    @Override
    public int hashCode() {
      return mask.hashCode();
    }

    @NotNull
    public abstract GitProperty createForChild();

    public abstract void applyAutoprops(@NotNull Map<String, String> props);
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
      final MimeType mimeType = getMimeType(tokens);
      if (mimeType != null) {
        parsedRules.add(new MimeRule(tokens[0], mimeType));
        if (mimeType == MimeType.BINARY) {
          continue;
        }
      }
      final String eol = getEol(tokens);
      if (eol != null) {
        parsedRules.add(new EolRule(tokens[0], eol));
      }
    }
    if (parsedRules.isEmpty()) return emptyRules;
    return parsedRules.toArray(new Rule[parsedRules.size()]);
  }

  @Nullable
  private MimeType getMimeType(String[] tokens) {
    for (int i = 1; i < tokens.length; ++i) {
      String token = tokens[i];
      int index = token.indexOf('=');
      switch (index < 0 ? token : token.substring(0, index)) {
        case "+binary":
        case "binary":
          return MimeType.BINARY;
        case "+text":
        case "text":
          return MimeType.TEXT;
        case "-binary":
        case "-text":
          return MimeType.UNKNOWN;
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
        rule.applyAutoprops(autoprops);
      }
      final StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, String> entry : autoprops.entrySet()) {
        sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
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

  private final static class EolRule extends Rule {
    @NotNull
    private final String eol;

    private EolRule(@NotNull String mask, @NotNull String eol) {
      super(mask);
      this.eol = eol;
    }

    @NotNull
    @Override
    public GitProperty createForChild() {
      return new GitEolFile(eol);
    }

    @Override
    public void applyAutoprops(@NotNull Map<String, String> props) {
      props.put(getMask() + " = " + SVNProperty.EOL_STYLE, eol);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      final EolRule eolRule = (EolRule) o;
      return eol.equals(eolRule.eol);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + eol.hashCode();
      return result;
    }
  }

  private final static class MimeRule extends Rule {
    @NotNull
    private final MimeType mimeType;

    private MimeRule(@NotNull String mask, @NotNull MimeType mimeType) {
      super(mask);
      this.mimeType = mimeType;
    }

    @NotNull
    @Override
    public GitProperty createForChild() {
      return new GitMimeFile(mimeType);
    }

    @Override
    public void applyAutoprops(@NotNull Map<String, String> props) {
      final String key = getMask() + " = " + SVNProperty.MIME_TYPE;
      if (mimeType == MimeType.BINARY) {
        props.put(key, MIME_BINARY);
      } else {
        props.remove(key);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      final MimeRule mimeRule = (MimeRule) o;
      return mimeType == mimeRule.mimeType;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + mimeType.hashCode();
      return result;
    }
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
            return rule.createForChild();
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

  private static final class GitMimeFile implements GitProperty {
    @NotNull
    private final MimeType mimeType;

    private GitMimeFile(@NotNull MimeType mimeType) {
      this.mimeType = mimeType;
    }

    @Override
    public void apply(@NotNull Map<String, String> props) {
      if (mimeType == MimeType.BINARY) {
        props.put(SVNProperty.MIME_TYPE, MIME_BINARY);
      } else {
        props.remove(SVNProperty.MIME_TYPE);
      }
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

      final GitMimeFile that = (GitMimeFile) o;
      return mimeType.equals(that.mimeType);
    }

    @Override
    public int hashCode() {
      return mimeType.hashCode();
    }
  }
}
