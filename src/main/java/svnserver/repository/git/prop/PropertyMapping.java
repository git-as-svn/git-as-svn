package svnserver.repository.git.prop;

import org.jetbrains.annotations.NotNull;
import svnserver.repository.VcsFunction;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public enum PropertyMapping {
  DotTGitConfig(".tgitconfig", GitTortoise::new),
  DotGitAttributes(".gitattributes", GitAttributes::new),
  DitGitIgnore(".gitignore", GitIgnore::new);

  @NotNull
  private final String fileName;

  @NotNull
  public VcsFunction<String, GitProperty> getParser() {
    return parser;
  }

  @NotNull
  private final VcsFunction<String, GitProperty> parser;

  @NotNull
  public static final Map<String, PropertyMapping> byFileName = new HashMap<>();

  static {
    for (PropertyMapping parser : values())
      byFileName.put(parser.fileName, parser);
  }

  PropertyMapping(@NotNull String fileName, @NotNull VcsFunction<String, GitProperty> parser) {
    this.fileName = fileName;
    this.parser = parser;
  }
}
