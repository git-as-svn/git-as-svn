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
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public interface LockManagerWrite extends LockManagerRead {

  @NotNull
  LockDesc[] lock(@NotNull SessionContext context, @Nullable String comment, boolean stealLock, @NotNull LockTarget[] targets) throws SVNException, IOException;

  void unlock(@NotNull SessionContext context, boolean breakLock, @NotNull UnlockTarget[] targets) throws SVNException;

  void validateLocks() throws SVNException;

  void renewLocks(@NotNull LockDesc[] locks) throws IOException, SVNException;
}
