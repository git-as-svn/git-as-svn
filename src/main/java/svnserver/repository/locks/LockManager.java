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
import svnserver.repository.Depth;

import java.util.Iterator;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public interface LockManager {

  @NotNull
  LockDesc[] lock(@NotNull String username, @Nullable String comment, boolean stealLock, @NotNull LockTarget[] targets) throws SVNException;

  @NotNull
  default LockDesc lock(@NotNull String username, @Nullable String comment, boolean stealLock, @NotNull LockTarget target) throws SVNException {
    final LockDesc[] locks = lock(username, comment, stealLock, new LockTarget[]{target});
    if (locks.length != 1) {
      throw new IllegalStateException();
    }
    return locks[0];
  }

  @NotNull
  Iterator<LockDesc> getLocks(@NotNull String path, @NotNull Depth depth) throws SVNException;

  @Nullable
  LockDesc getLock(@NotNull String path) throws SVNException;

  void unlock(boolean breakLock, @NotNull UnlockTarget[] targets) throws SVNException;

  default void unlock(boolean breakLock, @NotNull UnlockTarget target) throws SVNException {
    unlock(breakLock, new UnlockTarget[]{target});
  }
}
