/*
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
import ru.bozaro.gitlfs.common.LockConflictException;
import ru.bozaro.gitlfs.common.VerifyLocksResult;
import svnserver.auth.User;
import svnserver.repository.Depth;
import svnserver.repository.git.GitBranch;

import java.io.IOException;
import java.util.Iterator;

public interface LockStorage {

  @NotNull
  LockDesc lock(@NotNull User user, @Nullable GitBranch branch, @NotNull String path) throws LockConflictException, IOException, SVNException;

  @Nullable
  LockDesc unlock(@NotNull User user, @Nullable GitBranch branch, boolean breakLock, @NotNull String lockId) throws LockConflictException, IOException, SVNException;

  @NotNull
  LockDesc[] getLocks(@NotNull User user, @Nullable GitBranch branch, @Nullable String path, @Nullable String lockId) throws IOException;

  @NotNull
  VerifyLocksResult verifyLocks(@NotNull User user, @Nullable GitBranch branch) throws IOException;

  @NotNull
  LockDesc[] unlock(@NotNull User user, @Nullable GitBranch branch, boolean breakLock, @NotNull UnlockTarget[] targets) throws LockConflictException, IOException, SVNException;

  @NotNull
  LockDesc[] lock(@NotNull User user, @Nullable GitBranch branch, @Nullable String comment, boolean stealLock, @NotNull LockTarget[] targets) throws LockConflictException, IOException, SVNException;

  boolean cleanupInvalidLocks(@NotNull GitBranch branch) throws IOException;

  void renewLocks(@NotNull GitBranch branch, @NotNull LockDesc[] lockDescs) throws IOException;

  @NotNull
  Iterator<LockDesc> getLocks(@NotNull User user, @NotNull GitBranch branch, @NotNull String path, @NotNull Depth depth) throws IOException, SVNException;
}
