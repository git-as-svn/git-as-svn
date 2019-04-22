/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.context;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Base context object.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ThreadSafe
public abstract class Context<S extends AutoCloseable> implements AutoCloseable {
  @NotNull
  private final ConcurrentHashMap<Class<? extends S>, S> map = new ConcurrentHashMap<>();

  @NotNull
  protected Collection<S> values() {
    return map.values();
  }

  @NotNull
  public <T extends S> T add(@NotNull Class<T> type, @NotNull T object) {
    if (map.put(type, object) != null) {
      throw new IllegalStateException("Object with type " + type.getName() + " is already exists in shared context.");
    }
    return object;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public <T extends S> T get(@NotNull Class<T> type) {
    return (T) map.get(type);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public <T extends S> T remove(@NotNull Class<T> type) {
    return (T) map.remove(type);
  }

  @NotNull
  public <T extends S> T sure(@NotNull Class<T> type) {
    final T result = get(type);
    if (result == null) {
      throw new IllegalStateException("Can't find object with type " + type.getName() + " in context");
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  @NotNull
  public <T extends S> T getOrCreate(@NotNull Class<T> type, @NotNull Supplier<T> supplier) {
    final T result = get(type);
    if (result == null) {
      final T newObj = supplier.get();
      final S oldObj = map.putIfAbsent(type, newObj);
      if (oldObj != null) {
        return (T) oldObj;
      }
      return newObj;
    }
    return result;
  }

  @Override
  public void close() throws Exception {
    for (S item : new ArrayList<>(map.values())) {
      item.close();
    }
  }
}
