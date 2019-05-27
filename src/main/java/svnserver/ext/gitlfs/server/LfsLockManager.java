/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import ru.bozaro.gitlfs.common.LockConflictException;
import ru.bozaro.gitlfs.common.VerifyLocksResult;
import ru.bozaro.gitlfs.common.data.Lock;
import ru.bozaro.gitlfs.common.data.Ref;
import ru.bozaro.gitlfs.server.ForbiddenError;
import ru.bozaro.gitlfs.server.LockManager;
import ru.bozaro.gitlfs.server.UnauthorizedError;
import svnserver.auth.User;
import svnserver.repository.locks.LockDesc;
import svnserver.repository.locks.LockTarget;
import svnserver.repository.locks.UnlockTarget;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
final class LfsLockManager implements LockManager {
  @NotNull
  private final LfsContentManager lfsContentManager;

  LfsLockManager(@NotNull LfsContentManager lfsContentManager) {
    this.lfsContentManager = lfsContentManager;
  }

  @Override
  public @NotNull LockRead checkDownloadAccess(@NotNull HttpServletRequest request) throws IOException, ForbiddenError, UnauthorizedError {
    final User user = lfsContentManager.checkDownload(request);
    return new LockWriteImpl(user);
  }

  @NotNull
  @Override
  public LockWrite checkUploadAccess(@NotNull HttpServletRequest request) throws IOException, ForbiddenError, UnauthorizedError {
    final User user = lfsContentManager.checkUpload(request);
    return new LockWriteImpl(user);
  }

  private final class LockWriteImpl implements LockWrite {
    @NotNull
    private final User user;

    private LockWriteImpl(@NotNull User user) {
      this.user = user;
    }

    @Override
    @NotNull
    public Lock lock(@NotNull String path, @Nullable Ref ref) throws LockConflictException, IOException {
      final LockDesc[] lock;
      try {
        lock = lfsContentManager.getStorage().lock(user, null, null, false, new LockTarget[]{new LockTarget(path, -1)});
      } catch (SVNException e) {
        throw new IOException(e);
      }
      return LockDesc.toLock(lock[0]);
    }

    @Override
    @Nullable
    public Lock unlock(@NotNull String lockId, boolean force, @Nullable Ref ref) throws LockConflictException, IOException {
      final LockDesc[] lock;
      try {
        lock = lfsContentManager.getStorage().unlock(user, null, force, new UnlockTarget[]{new UnlockTarget(null, lockId)});
      } catch (SVNException e) {
        throw new IOException(e);
      }
      return lock.length < 1 ? null : LockDesc.toLock(lock[0]);
    }

    @NotNull
    @Override
    public VerifyLocksResult verifyLocks(@Nullable Ref ref) throws IOException {
      return lfsContentManager.getStorage().verifyLocks(user, null);
    }

    @Override
    @NotNull
    public List<Lock> getLocks(@Nullable String path, @Nullable String lockId, @Nullable Ref ref) throws IOException {
      final LockDesc[] locks = lfsContentManager.getStorage().getLocks(user, null, path, lockId);
      return Arrays.stream(locks).map(LockDesc::toLock).collect(Collectors.toList());
    }
  }
}
