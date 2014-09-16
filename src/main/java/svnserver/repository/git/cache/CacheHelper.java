/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.cache;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Some cache methods.
 *
 * @author a.navrotskiy
 */
public final class CacheHelper {
  private static final Yaml yaml;

  static {
    final DumperOptions options = new DumperOptions();
    options.setPrettyFlow(true);
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

    yaml = new Yaml(new CacheConstructor(), new CacheRepresenter(), options);
    yaml.setBeanAccess(BeanAccess.FIELD);
  }

  private CacheHelper() {
  }

  @NotNull
  public static CacheRevision load(@NotNull InputStream stream) {
    return yaml.loadAs(stream, CacheRevision.class);
  }

  @NotNull
  public static CacheRevision load(@Nullable TreeWalk tree) throws IOException {
    if (tree == null) {
      return CacheRevision.empty;
    }
    final ObjectLoader loader = tree.getObjectReader().open(tree.getObjectId(0));
    try (InputStream stream = loader.openStream()) {
      return load(stream);
    }
  }

  public static void save(@NotNull OutputStream stream, @NotNull CacheRevision cache) {
    yaml.dump(cache, new OutputStreamWriter(stream, StandardCharsets.UTF_8));
  }

  @NotNull
  public static ObjectId save(@NotNull ObjectInserter inserter, @NotNull CacheRevision cache) throws IOException {
    try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      save(stream, cache);
      return inserter.insert(Constants.OBJ_BLOB, stream.toByteArray());
    }
  }

  public static class CacheConstructor extends Constructor {
    public CacheConstructor() {
      this.yamlConstructors.put(new Tag("!uuid"), new ObjectIdConstruct());

      addTypeDescription(new TypeDescription(CacheRevision.class, "!revision"));
    }

    private class ObjectIdConstruct extends AbstractConstruct {
      public Object construct(Node node) {
        return ObjectId.fromString((String) constructScalar((ScalarNode) node));
      }
    }
  }

  public static class CacheRepresenter extends Representer {
    public CacheRepresenter() {
      this.representers.put(ObjectId.class, new ObjectIdRepresent());
      this.representers.put(RevCommit.class, new ObjectIdRepresent());
      this.representers.put(RevTree.class, new ObjectIdRepresent());

      addClassTag(CacheRevision.class, new Tag("!revision"));
    }

    private class ObjectIdRepresent implements Represent {
      public Node representData(Object data) {
        final ObjectId objectId = (ObjectId) data;
        return representScalar(new Tag("!uuid"), objectId.name());
      }
    }
  }
}
