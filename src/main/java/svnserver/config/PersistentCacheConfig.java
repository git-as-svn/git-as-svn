/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.mapdb.DBException;
import org.mapdb.DBMaker;
import svnserver.config.serializer.ConfigType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent cache config.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("persistentCache")
public class PersistentCacheConfig implements CacheConfig {
  @SuppressWarnings("FieldCanBeLocal")
  @NotNull
  private String path = "git-as-svn.mapdb";
  private boolean enableTransactions = true;

  @NotNull
  @Override
  public DB createCache(@NotNull Path basePath) throws IOException {
    final Path cacheBase = ConfigHelper.joinPath(basePath, path);
    Files.createDirectories(cacheBase.getParent());

    try {
      final DBMaker.Maker maker = DBMaker.fileDB(cacheBase.toFile())
          .closeOnJvmShutdown()
          .fileMmapEnableIfSupported();

      if (enableTransactions)
        maker.transactionEnable();

      return maker
          .make();
    } catch (DBException e) {
      throw new DBException(String.format("Failed to open %s: %s", cacheBase, e.getMessage()), e);
    }
  }
}
