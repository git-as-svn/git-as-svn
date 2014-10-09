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
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.StringHelper;
import svnserver.repository.*;
import svnserver.server.SessionContext;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory lock manager.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class InMemoryLockFactory implements LockManagerFactory {
  @NotNull
  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
  @NotNull
  private TreeMap<String, LockDesc> locks = new TreeMap<>();

  @NotNull
  @Override
  public <T> T wrapLockRead(@NotNull VcsRepository repo, @NotNull LockWorker<T, LockManagerRead> work) throws IOException, SVNException {
    Lock lock = rwLock.readLock();
    lock.lock();
    try {
      return work.exec(new InMemoryLockManager(repo));
    } finally {
      lock.unlock();
    }
  }

  @NotNull
  @Override
  public <T> T wrapLockWrite(@NotNull VcsRepository repo, @NotNull LockWorker<T, LockManagerWrite> work) throws IOException, SVNException {
    Lock lock = rwLock.writeLock();
    lock.lock();
    try {
      return work.exec(new InMemoryLockManager(repo));
    } finally {
      lock.unlock();
    }
  }

  private class InMemoryLockManager implements LockManagerWrite {
    @NotNull
    private final VcsRepository repo;

    public InMemoryLockManager(@NotNull VcsRepository repo) {
      this.repo = repo;
    }

    @NotNull
    @Override
    public LockDesc[] lock(@NotNull SessionContext context, @Nullable String comment, boolean stealLock, @NotNull LockTarget[] targets) throws SVNException, IOException {
      final LockDesc[] result = new LockDesc[targets.length];
      if (targets.length > 0) {
        final VcsRevision revision = repo.getLatestRevision();
        // Create new locks list.
        for (int i = 0; i < targets.length; ++i) {
          final LockTarget target = targets[i];
          final VcsFile file = revision.getFile(target.getPath());
          if (file == null || file.isDirectory()) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, target.getPath()));
          }
          if (locks.containsKey(repo.getUuid() + "/" + target.getPath())) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_PATH_ALREADY_LOCKED, target.getPath()));
          }
          if (target.getRev() < file.getLastChange().getId()) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, target.getPath()));
          }
          result[i] = new LockDesc(targets[i].getPath(), createLockId(), context.getUser().getUserName(), comment, 0);
        }
        // Add locks.
        for (LockDesc lockDesc : result) {
          locks.put(repo.getUuid() + "/" + lockDesc.getPath(), lockDesc);
        }
      }
      return result;
    }

    @NotNull
    @Override
    public Iterator<LockDesc> getLocks(@NotNull String path, @NotNull Depth depth) throws SVNException {
      return depth.visit(new InMemoryLockVisitor(repo.getUuid() + "/" + path));
    }

    @Override
    public LockDesc getLock(@NotNull String path) {
      return locks.get(repo.getUuid() + "/" + path);
    }

    @Override
    public void unlock(@NotNull SessionContext context, boolean breakLock, @NotNull UnlockTarget[] targets) throws SVNException {
      for (UnlockTarget target : targets) {
        final LockDesc lock = locks.get(repo.getUuid() + "/" + target.getPath());
        if ((lock == null) || (!lock.getToken().equals(target.getToken()))) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_LOCK, target.getPath()));
        }
      }
      for (UnlockTarget target : targets) {
        locks.remove(repo.getUuid() + "/" + target.getPath());
      }
    }

    @Override
    public void validateLocks() throws SVNException {
    }
  }

  public class InMemoryLockVisitor implements DepthVisitor<Iterator<LockDesc>> {
    @NotNull
    private final String pathKey;

    public InMemoryLockVisitor(@NotNull String pathKey) {
      this.pathKey = pathKey;
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
      return null;
    }

    @NotNull
    @Override
    public Iterator<LockDesc> visitImmediates() throws SVNException {
      return visitFiles();
    }

    @NotNull
    @Override
    public Iterator<LockDesc> visitInfinity() throws SVNException {
      Iterator<Map.Entry<String, LockDesc>> iterator = locks.tailMap(pathKey, true).entrySet().iterator();
      return new Iterator<LockDesc>() {
        @Nullable
        private LockDesc nextItem = findNext();

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
              return item.getValue();
            }
          }
          return null;
        }
      };
    }
  }

  private static String createLockId() {
    return UUID.randomUUID().toString();
  }
}
