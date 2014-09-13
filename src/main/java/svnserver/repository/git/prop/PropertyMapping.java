package svnserver.repository.git.prop;

import org.atteo.classindex.ClassIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public class PropertyMapping {
  @NotNull
  private static final Map<String, GitPropertyFactory> parserByFile = new TreeMap<>();

  static {
    try {
      for (Class<? extends GitPropertyFactory> factoryClass : ClassIndex.getSubclasses(GitPropertyFactory.class)) {
        final GitPropertyFactory factory = factoryClass.getConstructor().newInstance();
        final GitPropertyFactory oldParser = parserByFile.put(factory.getFileName(), factory);
        if (oldParser != null) {
          throw new RuntimeException("Found two classes mapped for same file: " + oldParser.getClass() + " and " + factoryClass);
        }
      }
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  public static GitPropertyFactory getFactory(@NotNull String fileName) {
    return parserByFile.get(fileName);
  }

  @NotNull
  public static Collection<String> getRegisteredFiles() {
    return Collections.unmodifiableSet(parserByFile.keySet());
  }
}
