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

  @Override
  @NotNull
  public LockDesc[] lock(@NotNull User user, @Nullable GitBranch branch, @Nullable String comment, boolean stealLock, @NotNull LockTarget[] targets) throws LockConflictException, IOException, SVNException {
    final LockDesc[] result = new LockDesc[targets.length];

    if (targets.length > 0) {
      final GitRevision revision = branch == null ? null : branch.getLatestRevision();
      final String branchName = branch == null ? null : branch.getShortBranchName();
      // Create new locks list.
      for (int i = 0; i < targets.length; ++i) {
        final LockTarget target = targets[i];

        final String hash;
        if (revision != null) {
          final GitFile file = revision.getFile(target.getPath());
          if (file == null) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, target.getPath()));
          }
          if (file.isDirectory()) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, target.getPath()));
          }
          if (target.getRev() < file.getLastChange().getId()) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, target.getPath()));
          }

          hash = file.getContentHash();
        } else {
          hash = null;
        }

        final LockDesc currentLock = locks.get(target.getPath());
        if (!stealLock && currentLock != null)
          throw new LockConflictException(LockDesc.toLock(currentLock));

        result[i] = new LockDesc(target.getPath(), branchName, hash, createLockId(), user.getUserName(), comment, System.currentTimeMillis());
      }

      // Add locks.
      for (LockDesc lockDesc : result)
        locks.put(lockDesc.getPath(), lockDesc);
    }

    return result;
  }

  @NotNull
  private static String createLockId() {
    return UUID.randomUUID().toString();
  }

  @NotNull
  @Override
  public LockDesc[] unlock(@NotNull User user, @Nullable GitBranch branch, boolean breakLock, @NotNull UnlockTarget[] targets) throws LockConflictException, SVNException {
    final List<LockDesc> result = new ArrayList<>();
    for (UnlockTarget target : targets) {
      final LockDesc lock = locks.get(target.getPath());
      if (lock == null)
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_LOCK, target.getPath()));

      if (!breakLock && !lock.getToken().equals(target.getToken()))
        throw new LockConflictException(LockDesc.toLock(lock));
    }

    for (UnlockTarget target : targets)
      result.add(locks.remove(target.getPath()));

    return result.toArray(LockDesc.emptyArray);
  }

  @Override
  public boolean cleanupInvalidLocks(@NotNull GitBranch branch) throws IOException {
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
  public void renewLocks(@NotNull GitBranch branch, @NotNull LockDesc[] lockDescs) throws IOException {
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
  public LockDesc[] getLocks(@NotNull User user, @Nullable GitBranch branch, @Nullable String path, @Nullable String lockId) {
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
  public VerifyLocksResult verifyLocks(@NotNull User user, @Nullable GitBranch branch) {
    final List<Lock> ourLocks = new ArrayList<>();
    final List<Lock> theirLocks = new ArrayList<>();
    for (LockDesc lock : getLocks(user, branch, null, null))
      (user.getUserName().equals(lock.getOwner()) ? ourLocks : theirLocks).add(LockDesc.toLock(lock));

    return new VerifyLocksResult(ourLocks, theirLocks);
  }
}
