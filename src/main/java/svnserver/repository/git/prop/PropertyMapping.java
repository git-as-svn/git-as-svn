/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
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
