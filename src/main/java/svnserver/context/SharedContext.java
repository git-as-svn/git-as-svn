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
import org.tmatesoft.svn.core.SVNException;
import svnserver.config.SharedConfig;

import javax.annotation.concurrent.ThreadSafe;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Simple context object.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ThreadSafe
public final class SharedContext extends Context<Shared> implements AutoCloseable {
  @NotNull
  private final File basePath;
  @NotNull
  private final DB cacheDB;
  @NotNull
  private final ThreadPoolExecutor threadPoolExecutor;

  private SharedContext(@NotNull File basePath, @NotNull DB cacheDb, @NotNull ThreadPoolExecutor threadPoolExecutor) {
    this.basePath = basePath;
    this.cacheDB = cacheDb;
    this.threadPoolExecutor = threadPoolExecutor;
  }

  @NotNull
  public static SharedContext create(@NotNull File basePath, @NotNull DB cacheDb, @NotNull ThreadFactory threadFactory, @NotNull List<SharedConfig> shared) throws IOException, SVNException {
    final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new SynchronousQueue<>(), threadFactory);
    final SharedContext context = new SharedContext(basePath, cacheDb, threadPoolExecutor);
    for (SharedConfig config : shared) {
      config.create(context);
    }
    for (Shared item : new ArrayList<>(context.values())) {
      item.init(context);
    }
    return context;
  }

  @NotNull
  public ThreadPoolExecutor getThreadPoolExecutor() {
    return threadPoolExecutor;
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
  public File getBasePath() {
    return basePath;
  }

  @NotNull
  public DB getCacheDB() {
    return cacheDB;
  }
}
