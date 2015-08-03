/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.memory;

import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Local storage writer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsMemoryReader implements LfsReader {
  @NotNull
  private final byte[] content;

  public LfsMemoryReader(@NotNull byte[] content) {
    this.content = content;
  }

  @NotNull
  @Override
  public InputStream openStream() throws IOException {
    return new ByteArrayInputStream(content);
  }

  @Nullable
  @Override
  public InputStream openGzipStream() throws IOException {
    return null;
  }

  @Override
  public long getSize() {
    return content.length;
  }

  @NotNull
  @Override
  public String getMd5() {
    try {
      return Hex.encodeHexString(MessageDigest.getInstance("MD5").digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @NotNull
  @Override
  public String getOid(boolean hashOnly) {
    if (hashOnly) {
      return getSha();
    } else {
      return LfsStorage.OID_PREFIX + getSha();
    }
  }

  private String getSha() {
    try {
      return Hex.encodeHexString(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
