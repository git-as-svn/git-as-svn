package svnserver.config.serializer;

import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;
import svnserver.config.Config;
import svnserver.config.GitRepositoryConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Helper for parse/serialize configuration files.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ConfigSerializer {
  @NotNull
  private static final String TAG_HREF = "!file";
  @NotNull
  private final Yaml yaml;

  public ConfigSerializer(@NotNull File basePath) {
    final DumperOptions options = new DumperOptions();
    options.setPrettyFlow(true);
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

    yaml = new Yaml(new ConfigConstructor(basePath), new ConfigRepresenter(), options);
    yaml.setBeanAccess(BeanAccess.FIELD);
  }

  public void save(@NotNull OutputStream stream, @NotNull Config config) {
    yaml.dump(config, new OutputStreamWriter(stream, StandardCharsets.UTF_8));
  }

  @NotNull
  public String dump(Config config) {
    return yaml.dump(config);
  }

  @NotNull
  public Config load(@NotNull InputStream stream) {
    return yaml.loadAs(stream, Config.class);
  }

  @NotNull
  public Config load(@NotNull File file) throws IOException {
    try (InputStream stream = new FileInputStream(file)) {
      return load(stream);
    }
  }

  public static class ConfigConstructor extends Constructor {
    @NotNull
    private final File basePath;

    public ConfigConstructor(@NotNull File basePath) {
      this.basePath = basePath;
      this.yamlConstructors.put(new Tag(TAG_HREF), new FileConstruct());

      addTypeDescription(new TypeDescription(GitRepositoryConfig.class, "!repositoryGit"));
    }

    @Override
    protected Class<?> getClassForName(String name) throws ClassNotFoundException {
      return super.getClassForName(name);
    }

    private class FileConstruct extends AbstractConstruct {
      public Object construct(Node node) {
        return new File(basePath, (String) constructScalar((ScalarNode) node));
      }
    }
  }

  public static class ConfigRepresenter extends Representer {
    public ConfigRepresenter() {
      this.representers.put(File.class, new FileRepresent());

      addClassTag(GitRepositoryConfig.class, new Tag("!repositoryGit"));
    }

    private class FileRepresent implements Represent {
      public Node representData(Object data) {
        final File file = (File) data;
        return representScalar(new Tag(TAG_HREF), file.getAbsolutePath());
      }
    }
  }
}
