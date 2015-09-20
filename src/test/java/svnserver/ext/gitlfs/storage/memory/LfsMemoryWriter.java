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
import svnserver.HashHelper;
import svnserver.ext.gitlfs.storage.LfsWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local storage writer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsMemoryWriter extends LfsWriter {
  @NotNull
  private final ConcurrentHashMap<String, byte[]> storage;
  @NotNull
  private static final String OID_PREFIX = "sha256:";
  @Nullable
  private ByteArrayOutputStream stream;

  public LfsMemoryWriter(@NotNull ConcurrentHashMap<String, byte[]> storage) {
    this.storage = storage;
    this.stream = new ByteArrayOutputStream();
  }

  @Override
  public void write(int b) throws IOException {
    if (stream == null) {
      throw new IllegalStateException();
    }
    stream.write(b);
  }

  @Override
  public void write(@NotNull byte[] b, int off, int len) throws IOException {
    if (stream == null) {
      throw new IllegalStateException();
    }
    stream.write(b, off, len);
  }

  @NotNull
  @Override
  public String finish(@Nullable String expectedOid) throws IOException {
    if (stream == null) {
      throw new IllegalStateException();
    }
    final byte[] content = stream.toByteArray();
    String result = Hex.encodeHexString(HashHelper.sha256().digest(content));
    final String oid = OID_PREFIX + result;
    if (expectedOid != null && !expectedOid.equals(oid)) {
      throw new IOException("Invalid stream checksum: expected " + expectedOid + ", but actual " + oid);
    }
    storage.putIfAbsent(oid, content);
    stream = null;
    return oid;
  }

  @Override
  public void close() throws IOException {
    stream = null;
  }
}
