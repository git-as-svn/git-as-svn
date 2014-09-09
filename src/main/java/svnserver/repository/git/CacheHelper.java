package svnserver.repository.git;

import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import svnserver.repository.VcsCopyFrom;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Some cache methods.
 *
 * @author a.navrotskiy
 */
public class CacheHelper {
  @SuppressWarnings("unchecked")
  @NotNull
  public static Map<String, VcsCopyFrom> loadCopyFroms(@NotNull InputStream stream) {
    final Constructor constructor = new Constructor();
    constructor.addTypeDescription(new TypeDescription(VcsCopyFrom.class, "!copy"));

    final Yaml yaml = new Yaml(constructor);
    yaml.setBeanAccess(BeanAccess.FIELD);
    return yaml.loadAs(stream, Map.class);
  }

  public static void saveCopyFroms(@NotNull OutputStream stream, @NotNull Map<String, VcsCopyFrom> copyFroms) {
    final Representer representer = new Representer();
    representer.addClassTag(VcsCopyFrom.class, new Tag("!copy"));

    final Yaml yaml = new Yaml(representer);
    yaml.setBeanAccess(BeanAccess.FIELD);
    yaml.dump(copyFroms, new OutputStreamWriter(stream, StandardCharsets.UTF_8));
  }

  @NotNull
  public static Map<String, GitLogPair> loadChanges(@NotNull RevCommit commit) {
    return null;
  }
}
