/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.memory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsWriter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memory storage for LFS files.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsMemoryStorage implements LfsStorage {
  @NotNull
  private final ConcurrentHashMap<String, byte[]> storage = new ConcurrentHashMap<>();

  @Nullable
  @Override
  public LfsReader getReader(@NotNull String oid) throws IOException {
    final byte[] content = storage.get(oid);
    if (content == null) {
      return null;
    }
    return new LfsMemoryReader(content);
  }

  @NotNull
  @Override
  public LfsWriter getWriter() throws IOException {
    return new LfsMemoryWriter(storage);
  }
}
