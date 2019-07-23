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
import org.mapdb.Serializer;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import ru.bozaro.gitlfs.common.LockConflictException;
import ru.bozaro.gitlfs.common.VerifyLocksResult;
import ru.bozaro.gitlfs.common.data.Lock;
import svnserver.StringHelper;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.repository.Depth;
import svnserver.repository.git.GitBranch;
import svnserver.repository.git.GitFile;
import svnserver.repository.git.GitRevision;

import java.io.IOException;
import java.util.*;

/**
 * Lock manager.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public class LocalLockManager implements LockStorage {

  private static final int lockDescCacheVersion = 3;
  @NotNull
  private final SortedMap<String, LockDesc> locks;

  public LocalLockManager(@NotNull SortedMap<String, LockDesc> locks) {
    this.locks = locks;
  }

  @NotNull
  public static SortedMap<String, LockDesc> getPersistentStorage(@NotNull LocalContext context) {
    final String lockCacheName = String.format("locks.%s.%s", context.getName(), lockDescCacheVersion);
    return context.getShared().getCacheDB().treeMap(
        lockCacheName, Serializer.STRING, LockDescSerializer.instance
    ).createOrOpen();
  }

  @NotNull
  @Override
  public LockDesc lock(@NotNull User user, @Nullable GitBranch branch, @NotNull String path) throws LockConflictException, IOException, SVNException {
    final LockDesc lock = tryCreateLock(user, null, false, null, path, -1);
    locks.put(path, lock);
    return lock;
  }

  @Nullable
  @Override
  public LockDesc unlock(@NotNull User user, @Nullable GitBranch branch, boolean breakLock, @NotNull String lockId) throws LockConflictException {
    LockDesc result = null;

    for (Iterator<Map.Entry<String, LockDesc>> it = locks.entrySet().iterator(); it.hasNext(); ) {
      final Map.Entry<String, LockDesc> lock = it.next();

      if (!lockId.equals(lock.getValue().getToken()))
        continue;

      if (!breakLock && !user.getUserName().equals(lock.getValue().getOwner()))
        throw new LockConflictException(LockDesc.toLock(lock.getValue()));

      result = lock.getValue();
      it.remove();
      break;
    }

    return result;
  }

  @NotNull
  @Override
  public final LockDesc[] getLocks(@NotNull User user, @Nullable GitBranch branch, @Nullable String path, @Nullable String lockId) {
    final List<LockDesc> result = new ArrayList<>();
    for (Map.Entry<String, LockDesc> entry : locks.entrySet()) {
      final LockDesc lockDesc = entry.getValue();
      if (branch != null && lockDesc.getBranch() != null && !branch.getShortBranchName().equals(lockDesc.getBranch()))
        continue;

      if (path != null && !StringHelper.isParentPath(path, lockDesc.getPath()))
        continue;

      if (lockId != null && !lockDesc.getToken().equals(lockId))
        continue;

      result.add(lockDesc);
    }

    return result.toArray(LockDesc.emptyArray);
  }

  @Override
  @NotNull
  public final VerifyLocksResult verifyLocks(@NotNull User user, @Nullable GitBranch branch) {
    final List<Lock> ourLocks = new ArrayList<>();
    final List<Lock> theirLocks = new ArrayList<>();
    for (LockDesc lock : getLocks(user, branch, null, (String) null))
      (user.getUserName().equals(lock.getOwner()) ? ourLocks : theirLocks).add(LockDesc.toLock(lock));

    return new VerifyLocksResult(ourLocks, theirLocks);
  }

  @NotNull
  @Override
  public final LockDesc[] unlock(@NotNull User user, @Nullable GitBranch branch, boolean breakLock, @NotNull UnlockTarget[] targets) throws LockConflictException, SVNException {
    final List<LockDesc> result = new ArrayList<>();
    for (UnlockTarget target : targets) {
      final String path = target.getPath();
      final String token = target.getToken();

      final LockDesc lock = locks.get(path);
      if (lock == null)
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_LOCK, path));

      if (!breakLock && (!lock.getToken().equals(token) || !user.getUserName().equals(lock.getOwner())))
        throw new LockConflictException(LockDesc.toLock(lock));
    }

    for (UnlockTarget target : targets)
      result.add(locks.remove(target.getPath()));

    return result.toArray(LockDesc.emptyArray);
  }

  @Override
  @NotNull
  public final LockDesc[] lock(@NotNull User user, @Nullable GitBranch branch, @Nullable String comment, boolean stealLock, @NotNull LockTarget[] targets) throws LockConflictException, IOException, SVNException {
    final LockDesc[] result = new LockDesc[targets.length];

    if (targets.length > 0) {
      // Create new locks list.
      for (int i = 0; i < targets.length; ++i) {
        final LockTarget target = targets[i];
        final String path = target.getPath();
        final int targetRev = target.getRev();

        result[i] = tryCreateLock(user, comment, stealLock, branch, path, targetRev);
      }

      // Add locks.
      for (LockDesc lockDesc : result)
        locks.put(lockDesc.getPath(), lockDesc);
    }

    return result;
  }

  @Override
  public final boolean cleanupInvalidLocks(@NotNull GitBranch branch) throws IOException {
    boolean changed = false;

    final GitRevision revision = branch.getLatestRevision();
    final Iterator<Map.Entry<String, LockDesc>> iter = locks.entrySet().iterator();
    while (iter.hasNext()) {
      final LockDesc item = iter.next().getValue();
      if (!branch.getShortBranchName().equals(item.getBranch()))
        continue;

      final GitFile file = revision.getFile(item.getPath());
      if (file == null || file.isDirectory() || !file.getContentHash().equals(item.getHash())) {
        iter.remove();
        changed = true;
      }
    }

    return changed;
  }

  @Override
  public final void renewLocks(@NotNull GitBranch branch, @NotNull LockDesc[] lockDescs) throws IOException {
    final GitRevision revision = branch.getLatestRevision();
    for (LockDesc lockDesc : lockDescs) {
      final String pathKey = lockDesc.getPath();
      if (!locks.containsKey(pathKey)) {
        final GitFile file = revision.getFile(lockDesc.getPath());
        if (file != null && !file.isDirectory()) {
          locks.put(pathKey, new LockDesc(lockDesc.getPath(), branch.getShortBranchName(), file.getContentHash(), lockDesc.getToken(), lockDesc.getOwner(), lockDesc.getComment(), lockDesc.getCreated()));
        }
      }
    }
  }

  @NotNull
  @Override
  public final Iterator<LockDesc> getLocks(@NotNull User user, @NotNull GitBranch branch, @NotNull String path, @NotNull Depth depth) throws SVNException {
    return depth.visit(new TreeMapLockDepthVisitor(locks, path));
  }

  @NotNull
  private LockDesc tryCreateLock(@NotNull User user,
                                 @Nullable String comment,
                                 boolean stealLock,
                                 @Nullable GitBranch branch,
                                 @NotNull String path,
                                 int targetRev) throws IOException, SVNException, LockConflictException {

    final String hash;
    if (branch != null) {
      final GitRevision revision = branch.getLatestRevision();
      final GitFile file = revision.getFile(path);
      if (file == null)
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, path));

      if (file.isDirectory())
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, path));

      if (targetRev >= 0 && targetRev < file.getLastChange().getId())
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, path));

      hash = file.getContentHash();
    } else {
      hash = null;
    }

    final LockDesc currentLock = locks.get(path);
    if (!stealLock && currentLock != null)
      throw new LockConflictException(LockDesc.toLock(currentLock));

    return new LockDesc(path, branch, hash, createLockId(), user.getUserName(), comment, System.currentTimeMillis());
  }

  @NotNull
  private static String createLockId() {
    return UUID.randomUUID().toString();
  }
}
