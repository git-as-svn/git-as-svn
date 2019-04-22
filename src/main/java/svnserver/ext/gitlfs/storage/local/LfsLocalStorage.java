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
import org.slf4j.LoggerFactory;
import svnserver.auth.User;
import svnserver.ext.gitlfs.config.LocalLfsConfig;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsWriter;

import java.io.File;
import java.io.IOException;

/**
 * Local directory storage for LFS files.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class LfsLocalStorage implements LfsStorage {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(LfsLocalStorage.class);

  @NotNull
  public static final String HASH_MD5 = "hash-md5";
  @NotNull
  public static final String CREATE_TIME = "create-time";
  @NotNull
  public static final String META_EMAIL = "author-email";
  @NotNull
  public static final String META_USER_NAME = "author-login";
  @NotNull
  public static final String META_REAL_NAME = "author-name";

  @NotNull
  private final LocalLfsConfig.LfsLayout layout;
  @NotNull
  private final File dataRoot;
  @Nullable
  private final File metaRoot;
  private final boolean compress;

  public LfsLocalStorage(@NotNull LocalLfsConfig.LfsLayout layout, @NotNull File dataRoot, @Nullable File metaRoot, boolean compress) {
    this.layout = layout;
    this.dataRoot = dataRoot;
    this.metaRoot = metaRoot;
    this.compress = compress && (metaRoot != null);
    if (compress && (metaRoot == null)){
      log.error("Compression not supported for local LFS storage without metadata. Compression is disabled");
    }
  }

  @Nullable
  @Override
  public LfsReader getReader(@NotNull String oid) throws IOException {
    return LfsLocalReader.create(layout, dataRoot, metaRoot, oid);
  }

  @NotNull
  @Override
  public LfsWriter getWriter(@NotNull User user) throws IOException {
    return new LfsLocalWriter(layout, dataRoot, metaRoot, compress, user);
  }

  @Nullable
  static File getPath(@NotNull LocalLfsConfig.LfsLayout layout, @NotNull File root, @NotNull String oid, @NotNull String suffix) {
    if (!oid.startsWith(OID_PREFIX)) return null;
    final int offset = OID_PREFIX.length();
    return new File(root, layout.getPath(oid.substring(offset)) + suffix);
  }
}
