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
import svnserver.repository.VcsFile;
import svnserver.repository.VcsRepository;
import svnserver.repository.VcsRevision;
import svnserver.server.SessionContext;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.UUID;

/**
 * Map lock manager.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class TreeMapLockManager implements LockManagerWrite {
  private final static char SEPARATOR = ':';

  @NotNull
  private final VcsRepository repo;
  @NotNull
  private final SortedMap<String, LockDesc> locks;

  public TreeMapLockManager(@NotNull VcsRepository repo, @NotNull SortedMap<String, LockDesc> locks) {
    this.locks = locks;
    this.repo = repo;
  }

  @NotNull
  @Override
  public LockDesc[] lock(@NotNull SessionContext context, @Nullable String comment, boolean stealLock, @NotNull LockTarget[] targets) throws SVNException, IOException {
    final LockDesc[] result = new LockDesc[targets.length];
    if (targets.length > 0) {
      final VcsRevision revision = repo.getLatestRevision();
      // Create new locks list.
      for (int i = 0; i < targets.length; ++i) {
        final LockTarget target = targets[i];
        final VcsFile file = revision.getFile(target.getPath());
        if (file == null) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, target.getPath()));
        }
        if (file.isDirectory()) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, target.getPath()));
        }
        if ((!stealLock) && locks.containsKey(repo.getUuid() + SEPARATOR + target.getPath())) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_PATH_ALREADY_LOCKED, target.getPath()));
        }
        if (target.getRev() < file.getLastChange().getId()) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_OUT_OF_DATE, target.getPath()));
        }
        result[i] = new LockDesc(file.getFullPath(), file.getContentHash(), createLockId(), context.getUser().getUserName(), comment, 0);
      }
      // Add locks.
      for (LockDesc lockDesc : result) {
        locks.put(repo.getUuid() + SEPARATOR + lockDesc.getPath(), lockDesc);
      }
    }
    return result;
  }

  @NotNull
  @Override
  public Iterator<LockDesc> getLocks(@NotNull String path, @NotNull Depth depth) throws SVNException {
    return depth.visit(new TreeMapLockDepthVisitor(locks, repo.getUuid() + SEPARATOR + path));
  }

  @Override
  public LockDesc getLock(@NotNull String path) {
    return locks.get(repo.getUuid() + SEPARATOR + path);
  }

  @Override
  public void unlock(@NotNull SessionContext context, boolean breakLock, @NotNull UnlockTarget[] targets) throws SVNException {
    for (UnlockTarget target : targets) {
      final LockDesc lock = locks.get(repo.getUuid() + SEPARATOR + target.getPath());
      if ((lock == null) || (!(breakLock || lock.getToken().equals(target.getToken())))) {
        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_LOCK, target.getPath()));
      }
    }
    for (UnlockTarget target : targets) {
      locks.remove(repo.getUuid() + SEPARATOR + target.getPath());
    }
  }

  @Override
  public void validateLocks() throws SVNException {
    try {
      final VcsRevision revision = repo.getLatestRevision();
      final Iterator<Map.Entry<String, LockDesc>> iter = locks.entrySet().iterator();
      while (iter.hasNext()) {
        final Map.Entry<String, LockDesc> entry = iter.next();
        final LockDesc item = entry.getValue();
        final VcsFile file = revision.getFile(item.getPath());
        if ((file == null) || file.isDirectory() || (!item.getHash().equals(file.getContentHash()))) {
          iter.remove();
        }
      }
    } catch (IOException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e));
    }
  }

  @Override
  public void renewLocks(@NotNull LockDesc[] lockDescs) throws IOException, SVNException {
    final VcsRevision revision = repo.getLatestRevision();
    for (LockDesc lockDesc : lockDescs) {
      final String pathKey = repo.getUuid() + SEPARATOR + lockDesc.getPath();
      if (!locks.containsKey(pathKey)) {
        final VcsFile file = revision.getFile(lockDesc.getPath());
        if ((file != null) && (!file.isDirectory())) {
          locks.put(pathKey, new LockDesc(lockDesc.getPath(), file.getContentHash(), lockDesc.getToken(), lockDesc.getOwner(), lockDesc.getComment(), lockDesc.getCreated()));
        }
      }
    }
  }

  private static String createLockId() {
    return UUID.randomUUID().toString();
  }
}
