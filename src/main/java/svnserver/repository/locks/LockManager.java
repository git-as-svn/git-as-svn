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
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.User;
import svnserver.repository.Depth;
import svnserver.repository.git.GitBranch;
import svnserver.repository.git.GitFile;
import svnserver.repository.git.GitRevision;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.UUID;

/**
 * Lock manager.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class LockManager {

  @NotNull
  private final SortedMap<String, LockDesc> locks;

  public LockManager(@NotNull SortedMap<String, LockDesc> locks) {
    this.locks = locks;
  }

  @NotNull
  public LockDesc[] lock(@NotNull User user, @NotNull GitBranch branch, @Nullable String comment, boolean stealLock, @NotNull LockTarget[] targets) throws SVNException, IOException {
    final LockDesc[] result = new LockDesc[targets.length];
    if (targets.length > 0) {
      final GitRevision revision = branch.getLatestRevision();
      // Create new locks list.
      for (int i = 0; i < targets.length; ++i) {
        final LockTarget target = targets[i];
        final GitFile file = revision.getFile(target.getPath());
        if (file == null) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, target.getPath()));
        }
        if (file.isDirectory()) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, target.getPath()));
        }
        final LockDesc currentLock = locks.get(target.getPath());
        if (!stealLock && currentLock != null) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_PATH_ALREADY_LOCKED, "Path is already locked by {1}: {0}", target.getPath(), currentLock.getOwner()));
        }
        if (target.getRev() < file.getLastChange().getId()) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, target.getPath()));
        }
        result[i] = new LockDesc(file.getFullPath(), branch.getShortBranchName(), file.getContentHash(), createLockId(), user.getUserName(), comment, 0);
      }
      // Add locks.
      for (LockDesc lockDesc : result) {
        locks.put(lockDesc.getPath(), lockDesc);
      }
    }
    return result;
  }

  @NotNull
  private static String createLockId() {
    return UUID.randomUUID().toString();
  }

  public void unlock(boolean breakLock, @NotNull UnlockTarget[] targets) throws SVNException {
    for (UnlockTarget target : targets) {
      final LockDesc lock = locks.get(target.getPath());
      if (lock == null || !(breakLock || lock.getToken().equals(target.getToken()))) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_LOCK, target.getPath()));
      }
    }
    for (UnlockTarget target : targets) {
      locks.remove(target.getPath());
    }
  }

  public boolean cleanupInvalidLocks(@NotNull GitBranch branch) throws SVNException {
    boolean changed = false;
    try {
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
    } catch (IOException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e));
    }

    return changed;
  }

  public void renewLocks(@NotNull GitBranch branch, @NotNull LockDesc[] lockDescs) throws IOException, SVNException {
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
  public Iterator<LockDesc> getLocks(@NotNull String path, @NotNull Depth depth) throws SVNException {
    return depth.visit(new TreeMapLockDepthVisitor(locks, path));
  }

  public LockDesc getLock(@NotNull String path) {
    return locks.get(path);
  }
}
