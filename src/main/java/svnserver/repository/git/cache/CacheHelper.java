package svnserver.repository.git.cache;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import svnserver.repository.VcsCopyFrom;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Some cache methods.
 *
 * @author a.navrotskiy
 */
public final class CacheHelper {
  private CacheHelper() {
  }

  @NotNull
  public static CacheRevision load(@NotNull InputStream stream) {
    final Constructor constructor = new Constructor();
    constructor.addTypeDescription(new TypeDescription(VcsCopyFrom.class, "!copy"));

    final Yaml yaml = new Yaml(constructor);
    yaml.setBeanAccess(BeanAccess.FIELD);
    return yaml.loadAs(stream, CacheRevision.class);
  }

  public static void save(@NotNull OutputStream stream, @NotNull CacheRevision cache) {
    final Representer representer = new Representer();
    representer.addClassTag(VcsCopyFrom.class, new Tag("!copy"));

    final Yaml yaml = new Yaml(representer);
    yaml.setBeanAccess(BeanAccess.FIELD);
    yaml.dump(cache, new OutputStreamWriter(stream, StandardCharsets.UTF_8));
  }

  @NotNull
  public static ObjectId save(@NotNull ObjectInserter inserter, @NotNull CacheRevision cache) throws IOException {
    try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      save(stream, cache);
      return inserter.insert(Constants.OBJ_BLOB, stream.toByteArray());
    }
  }
}
