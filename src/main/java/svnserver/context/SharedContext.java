/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.context;

import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import svnserver.config.SharedConfig;

import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple context object.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ThreadSafe
public final class SharedContext extends Context<Shared> implements AutoCloseable {
  @NotNull
  private final Path basePath;
  @NotNull
  private final DB cacheDB;
  @NotNull
  private final String realm;

  private SharedContext(@NotNull Path basePath, @NotNull DB cacheDb, @NotNull String realm) {
    this.basePath = basePath;
    this.cacheDB = cacheDb;
    this.realm = realm;
  }

  @NotNull
  public static SharedContext create(@NotNull Path basePath, @NotNull String realm, @NotNull DB cacheDb, @NotNull List<SharedConfig> shared) throws Exception {
    final SharedContext context = new SharedContext(basePath, cacheDb, realm);
    for (SharedConfig config : shared) {
      config.create(context);
    }
    for (Shared item : new ArrayList<>(context.values())) {
      item.init(context);
    }
    return context;
  }

  @NotNull
  public String getRealm() {
    return realm;
  }

  public void ready() throws IOException {
    for (Shared item : new ArrayList<>(values())) {
      item.ready(this);
    }
  }

  @Override
  public void close() throws Exception {
    final List<Shared> values = new ArrayList<>(values());

    for (int i = values.size() - 1; i >= 0; --i)
      values.get(i).close();

    cacheDB.close();
  }

  @NotNull
  public Path getBasePath() {
    return basePath;
  }

  @NotNull
  public DB getCacheDB() {
    return cacheDB;
  }
}
