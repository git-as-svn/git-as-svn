/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.local;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Local directory storage for LFS files.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsLocalStorage implements LfsStorage {
  @NotNull
  static final byte[] HEADER = "LFS\0".getBytes(StandardCharsets.UTF_8);
  @NotNull
  private final File root;

  public LfsLocalStorage(@NotNull File root) {
    this.root = root;
  }

  @Nullable
  @Override
  public LfsReader getReader(@NotNull String sha256) throws IOException {
    try {
      return new LfsLocalReader(getPath(root, sha256));
    } catch (FileNotFoundException ignored) {
      return null;
    }
  }

  @NotNull
  @Override
  public LfsWriter getWriter() throws IOException {
    return new LfsLocalWriter(root);
  }

  static File getPath(@NotNull File root, @NotNull String sha256) {
    return new File(root, sha256.substring(0, 2) + "/" + sha256 + ".lfs");
  }

  public static MessageDigest createDigestMd5() {
    try {
      return MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public static MessageDigest createDigestSha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}