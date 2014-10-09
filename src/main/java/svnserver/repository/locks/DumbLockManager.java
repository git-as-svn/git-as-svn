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
import svnserver.repository.VcsRepository;
import svnserver.server.SessionContext;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class DumbLockManager implements LockManagerWrite, LockManagerFactory {

  private boolean readOnly;

  public DumbLockManager(boolean readOnly) {
    this.readOnly = readOnly;
  }

  @NotNull
  @Override
  public LockDesc[] lock(@NotNull SessionContext context, @Nullable String comment, boolean stealLock, @NotNull LockTarget[] targets) throws SVNException, IOException {
    checkReadOnly();
    final LockDesc[] result = new LockDesc[targets.length];
    for (int i = 0; i < targets.length; ++i)
      result[i] = new LockDesc(targets[i].getPath(), "", context.getUser().getUserName(), comment, 0);
    return result;
  }

  @NotNull
  @Override
  public Iterator<LockDesc> getLocks(@NotNull String path, @NotNull Depth depth) throws SVNException {
    return Collections.emptyIterator();
  }

  @Override
  public LockDesc getLock(@NotNull String path) {
    return null;
  }

  @Override
  public void unlock(@NotNull SessionContext context, boolean breakLock, @NotNull UnlockTarget[] targets) throws SVNException {
  }

  @Override
  public void validateLocks() throws SVNException {
  }

  private void checkReadOnly() throws SVNException {
    if (readOnly)
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE));
  }

  @NotNull
  @Override
  public <T> T wrapLockRead(@NotNull VcsRepository repository, @NotNull LockWorker<T, LockManagerRead> work) throws IOException, SVNException {
    return work.exec(this);
  }

  @NotNull
  @Override
  public <T> T wrapLockWrite(@NotNull VcsRepository repository, @NotNull LockWorker<T, LockManagerWrite> work) throws IOException, SVNException {
    return work.exec(this);
  }

}
