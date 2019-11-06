/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.local;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import svnserver.Loggers;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.ext.gitlfs.config.LocalLfsConfig;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsWriter;
import svnserver.repository.git.GitLocation;
import svnserver.repository.locks.LocalLockManager;
import svnserver.repository.locks.LockDesc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.SortedMap;

/**
 * Local directory storage for LFS files.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class LfsLocalStorage extends LocalLockManager implements LfsStorage {
  @NotNull
  static final String HASH_MD5 = "hash-md5";
  @NotNull
  static final String CREATE_TIME = "create-time";
  @NotNull
  static final String META_EMAIL = "author-email";
  @NotNull
  static final String META_USER_NAME = "author-login";
  @NotNull
  static final String META_REAL_NAME = "author-name";

  @NotNull
  private static final Logger log = Loggers.lfs;
  @NotNull
  private final LocalLfsConfig.LfsLayout layout;
  @NotNull
  private final Path dataRoot;
  @Nullable
  private final Path metaRoot;
  private final boolean compress;

  public LfsLocalStorage(@NotNull SortedMap<String, LockDesc> locks, @NotNull LocalLfsConfig.LfsLayout layout, @NotNull Path dataRoot, @Nullable Path metaRoot, boolean compress) {
    super(locks);
    this.layout = layout;
    this.dataRoot = dataRoot;
    this.metaRoot = metaRoot;
    this.compress = compress && (metaRoot != null);
    if (compress && (metaRoot == null)) {
      log.error("Compression not supported for local LFS storage without metadata. Compression is disabled");
    }
  }

  @Nullable
  static Path getPath(@NotNull LocalLfsConfig.LfsLayout layout, @NotNull Path root, @NotNull String oid, @NotNull String suffix) {
    if (!oid.startsWith(OID_PREFIX)) return null;
    final int offset = OID_PREFIX.length();
    return root.resolve(layout.getPath(oid.substring(offset)) + suffix);
  }

  @NotNull
  public static Path getMetaRoot(@NotNull LocalContext context) {
    return context.sure(GitLocation.class).getFullPath().resolve("lfs/meta");
  }

  @Nullable
  @Override
  public LfsReader getReader(@NotNull String oid, long size) throws IOException {
    return LfsLocalReader.create(layout, dataRoot, metaRoot, oid);
  }

  @NotNull
  @Override
  public LfsWriter getWriter(@NotNull User user) throws IOException {
    return new LfsLocalWriter(layout, dataRoot, metaRoot, compress, user);
  }
}
