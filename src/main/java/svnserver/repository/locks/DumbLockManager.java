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
import svnserver.repository.Depth;

import java.util.Collections;
import java.util.Iterator;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class DumbLockManager implements LockManager {

  private boolean readOnly;

  public DumbLockManager(boolean readOnly) {
    this.readOnly = readOnly;
  }

  @NotNull
  @Override
  public LockDesc[] lock(@NotNull String username, @Nullable String comment, boolean stealLock, @NotNull LockTarget[] targets) throws SVNException {
    checkReadOnly();

    final LockDesc[] result = new LockDesc[targets.length];
    for (int i = 0; i < targets.length; ++i)
      result[i] = lock(username, comment, stealLock, targets[i]);
    return result;
  }

  @Nullable
  @Override
  public LockDesc getLock(@NotNull String path) throws SVNException {
    return null;
  }

  @NotNull
  @Override
  public Iterator<LockDesc> getLocks(@NotNull String path, @NotNull Depth depth) throws SVNException {
    return Collections.<LockDesc>emptyList().iterator();
  }

  @Override
  public void unlock(boolean breakLock, @NotNull UnlockTarget[] targets) throws SVNException {
    checkReadOnly();
  }

  private void checkReadOnly() throws SVNException {
    if (readOnly)
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE));
  }
}
