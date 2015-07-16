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
import org.jetbrains.annotations.Nullable;
import org.mapdb.DB;
import org.tmatesoft.svn.core.SVNException;
import svnserver.config.SharedConfig;
import svnserver.repository.VcsSupplier;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple context object.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ThreadSafe
public class SharedContext implements AutoCloseable {
  @NotNull
  private final ConcurrentHashMap<Class<? extends Shared>, Shared> map = new ConcurrentHashMap<>();
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
    for (Shared item : new ArrayList<>(context.map.values())) {
      item.init(context);
    }
    return context;
  }

  public void ready() throws IOException {
    for (Shared item : new ArrayList<>(map.values())) {
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

  @NotNull
  public <T extends Shared> T add(@NotNull Class<T> type, @NotNull T object) {
    if (map.put(type, object) != null) {
      throw new IllegalStateException("Object with type " + type.getName() + " is already exists in shared context.");
    }
    return object;
  }

  @Nullable
  public <T extends Shared> T get(@NotNull Class<T> type) {
    //noinspection unchecked
    return (T) map.get(type);
  }

  @NotNull
  public <T extends Shared> T sure(@NotNull Class<T> type) {
    final T result = get(type);
    if (result == null) {
      throw new IllegalStateException("Can't find object with type " + type.getName() + " in context");
    }
    return result;
  }

  @NotNull
  public <T extends Shared> T getOrCreate(@NotNull Class<T> type, @NotNull VcsSupplier<T> supplier) throws IOException, SVNException {
    final T result = get(type);
    if (result == null) {
      final T newObj = supplier.get();
      final Shared oldObj = map.putIfAbsent(type, newObj);
      if (oldObj != null) {
        //noinspection unchecked
        return (T) oldObj;
      }
      return newObj;
    }
    return result;
  }
}
