/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.local;

import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.ext.gitlfs.storage.LfsWriter;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Local storage writer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsLocalWriter extends LfsWriter {
  @NotNull
  private final File root;
  @NotNull
  private final File file;

  @Nullable
  private RandomAccessFile randomAccessFile;
  @NotNull
  private final MessageDigest digestMd5;
  @NotNull
  private final MessageDigest digestSha;
  private long size;

  public LfsLocalWriter(@NotNull File root) throws IOException {
    this.root = root;
    final File tmp = new File(root, "tmp");
    //noinspection ResultOfMethodCallIgnored
    tmp.mkdirs();
    file = new File(tmp, UUID.randomUUID().toString() + ".lfs");
    file.deleteOnExit();

    randomAccessFile = new RandomAccessFile(file, "rw");
    digestMd5 = LfsLocalStorage.createDigestMd5();
    digestSha = LfsLocalStorage.createDigestSha256();
    size = -1;
    writeHeader();
    size = 0;
  }

  private void writeHeader() throws IOException {
    if (randomAccessFile == null) {
      throw new IllegalStateException();
    }
    randomAccessFile.write(LfsLocalStorage.HEADER);
    randomAccessFile.writeLong(size);
    final byte[] md5 = digestMd5.digest();
    randomAccessFile.writeByte(md5.length);
    randomAccessFile.write(md5);
  }

  @Override
  public void write(int b) throws IOException {
    if (randomAccessFile == null) {
      throw new IllegalStateException();
    }
    randomAccessFile.write(b);
    digestMd5.update((byte) b);
    digestSha.update((byte) b);
    size += 1;
  }

  @Override
  public void write(@NotNull byte[] b, int off, int len) throws IOException {
    if (randomAccessFile == null) {
      throw new IllegalStateException();
    }
    randomAccessFile.write(b, off, len);
    digestMd5.update(b, off, len);
    digestSha.update(b, off, len);
    size += len;
  }

  @NotNull
  @Override
  public String finish() throws IOException {
    if (randomAccessFile == null) {
      throw new IllegalStateException();
    }
    randomAccessFile.seek(0);
    writeHeader();
    randomAccessFile.close();

    final String hex = Hex.encodeHexString(digestSha.digest());
    final File newName = LfsLocalStorage.getPath(root, hex);
    //noinspection ResultOfMethodCallIgnored
    newName.getParentFile().mkdirs();
    if (!file.renameTo(newName)) {
      throw new IOException("Can't rename file: " + file.getPath() + " -> " + newName.getPath());
    }
    return hex;
  }

  @Override
  public void close() throws IOException {
    if (randomAccessFile != null) {
      //noinspection ResultOfMethodCallIgnored
      file.delete();
      randomAccessFile = null;
    }
  }
}
