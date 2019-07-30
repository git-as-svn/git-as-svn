/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.memory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.auth.User;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsWriter;
import svnserver.repository.locks.LocalLockManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Memory storage for LFS files.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class LfsMemoryStorage extends LocalLockManager implements LfsStorage {
  @NotNull
  private final ConcurrentHashMap<String, byte[]> files = new ConcurrentHashMap<>();

  public LfsMemoryStorage() {
    super(new ConcurrentSkipListMap<>());
  }

  @NotNull
  public Map<String, byte[]> getFiles() {
    return files;
  }

  @Nullable
  @Override
  public LfsReader getReader(@NotNull String oid, long size) {
    final byte[] content = files.get(oid);
    if (content == null)
      return null;

    return new LfsMemoryReader(content);
  }

  @NotNull
  @Override
  public LfsWriter getWriter(@NotNull User user) {
    return new LfsMemoryWriter(files);
  }
}
