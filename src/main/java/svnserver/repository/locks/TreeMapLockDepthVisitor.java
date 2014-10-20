/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.locks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.StringHelper;
import svnserver.repository.DepthVisitor;

import java.util.*;

/**
 * Depth visitor for lock iteration.
 * *
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class TreeMapLockDepthVisitor implements DepthVisitor<Iterator<LockDesc>> {
  @NotNull
  private SortedMap<String, LockDesc> locks;
  @NotNull
  private final String pathKey;

  public TreeMapLockDepthVisitor(@NotNull SortedMap<String, LockDesc> locks, @NotNull String pathKey) {
    this.pathKey = pathKey;
    this.locks = locks;
  }

  @NotNull
  @Override
  public Iterator<LockDesc> visitEmpty() throws SVNException {
    final LockDesc desc = locks.get(pathKey);
    return desc == null ? Collections.emptyIterator() : Arrays.asList(desc).iterator();
  }

  @NotNull
  @Override
  public Iterator<LockDesc> visitFiles() throws SVNException {
    return new LockDescIterator(locks, pathKey) {
      @Override
      protected boolean filter(@NotNull Map.Entry<String, LockDesc> item) {
        return pathKey.equals(item.getKey()) || pathKey.equals(StringHelper.parentDir(item.getKey()));
      }
    };
  }

  @NotNull
  @Override
  public Iterator<LockDesc> visitImmediates() throws SVNException {
    return visitFiles();
  }

  @NotNull
  @Override
  public Iterator<LockDesc> visitInfinity() throws SVNException {
    return new LockDescIterator(locks, pathKey) {
      @Override
      protected boolean filter(@NotNull Map.Entry<String, LockDesc> item) {
        return true;
      }
    };
  }

  @NotNull
  @Override
  public Iterator<LockDesc> visitUnknown() {
    return Collections.emptyIterator();
  }

  private static abstract class LockDescIterator implements Iterator<LockDesc> {
    @NotNull
    private final Iterator<Map.Entry<String, LockDesc>> iterator;
    @NotNull
    private final String pathKey;
    @Nullable
    private LockDesc nextItem;

    public LockDescIterator(@NotNull SortedMap<String, LockDesc> locks, @NotNull String pathKey) {
      this.iterator = locks.tailMap(pathKey).entrySet().iterator();
      this.pathKey = pathKey;
      this.nextItem = findNext();
    }

    @Override
    public boolean hasNext() {
      return nextItem != null;
    }

    @Override
    public LockDesc next() {
      LockDesc result = nextItem;
      if (result != null) {
        nextItem = findNext();
      }
      return result;
    }

    protected LockDesc findNext() {
      while (iterator.hasNext()) {
        Map.Entry<String, LockDesc> item = iterator.next();
        if (StringHelper.isParentPath(pathKey, item.getKey())) {
          if (filter(item)) {
            return item.getValue();
          }
        }
      }
      return null;
    }

    protected abstract boolean filter(@NotNull Map.Entry<String, LockDesc> item);
  }
}
