/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.locks;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.repository.VcsRepository;

import java.io.IOException;
import java.util.NavigableMap;
import java.util.TreeMap;
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
  private NavigableMap<String, LockDesc> locks = new TreeMap<>();

  @NotNull
  @Override
  public <T> T wrapLockRead(@NotNull VcsRepository repo, @NotNull LockWorker<T, LockManagerRead> work) throws IOException, SVNException {
    return wrapLock(rwLock.readLock(), repo, work);
  }

  @NotNull
  @Override
  public <T> T wrapLockWrite(@NotNull VcsRepository repo, @NotNull LockWorker<T, LockManagerWrite> work) throws IOException, SVNException {
    return wrapLock(rwLock.writeLock(), repo, work);
  }

  @NotNull
  private <T> T wrapLock(@NotNull Lock lock, @NotNull VcsRepository repo, @NotNull LockWorker<T, ? super TreeMapLockManager> work) throws IOException, SVNException {
    lock.lock();
    try {
      return work.exec(new TreeMapLockManager(repo, locks));
    } finally {
      lock.unlock();
    }
  }
}
