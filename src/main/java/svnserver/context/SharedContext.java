/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.context;

import org.apache.http.annotation.ThreadSafe;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.tmatesoft.svn.core.SVNException;
import svnserver.config.SharedConfig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple context object.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ThreadSafe
public class SharedContext extends Context<Shared> implements AutoCloseable {
  @NotNull
  private final File basePath;
  @NotNull
  private final DB cacheDB;

  public SharedContext(@NotNull File basePath, @NotNull DB cacheDb) {
    this.basePath = basePath;
    this.cacheDB = cacheDb;
  }

  public static SharedContext create(@NotNull File basePath, @NotNull DB cacheDb, @NotNull List<SharedConfig> shared) throws IOException, SVNException {
    final SharedContext context = new SharedContext(basePath, cacheDb);
    for (SharedConfig config : shared) {
      config.create(context);
    }
    for (Shared item : new ArrayList<>(context.values())) {
      item.init(context);
    }
    return context;
  }

  public void ready() throws IOException {
    for (Shared item : new ArrayList<>(values())) {
      item.ready(this);
    }
  }

  @Override
  public void close() throws IOException {
    cacheDB.close();
  }

  @NotNull
  public File getBasePath() {
    return basePath;
  }

  @NotNull
  public DB getCacheDB() {
    return cacheDB;
  }
}
