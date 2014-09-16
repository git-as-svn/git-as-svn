/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/**
 * Stream for write-then-read functionality.
 *
 * @author Artem V. Navrotskiy
 */
public class TemporaryOutputStream extends OutputStream {
  @SuppressWarnings("MagicNumber")
  private static final int MAX_MEMORY_SIZE = 8 * 1024 * 1024;

  private final int maxMemorySize;
  @NotNull
  private final ByteArrayOutputStream memoryStream = new ByteArrayOutputStream();
  @Nullable
  private File tempFile;
  @Nullable
  private FileOutputStream fileOutputStream;
  private long totalSize = 0;

  public TemporaryOutputStream() {
    this.maxMemorySize = MAX_MEMORY_SIZE;
  }

  public TemporaryOutputStream(int maxMemorySize) {
    this.maxMemorySize = maxMemorySize;
  }

  @Override
  public void write(int b) throws IOException {
    if (memoryStream.size() < maxMemorySize) {
      memoryStream.write(b);
      totalSize++;
      return;
    }
    ensureFile().write(b);
    totalSize++;
  }

  public long size() {
    return totalSize;
  }

  @NotNull
  private FileOutputStream ensureFile() throws IOException {
    if (fileOutputStream == null) {
      tempFile = File.createTempFile("tmp", "");
      tempFile.deleteOnExit();
      fileOutputStream = new FileOutputStream(tempFile);
    }
    return fileOutputStream;
  }

  @Override
  public void write(@NotNull byte[] b, int off, int len) throws IOException {
    if (memoryStream.size() < maxMemorySize) {
      final int size = Math.min(maxMemorySize - memoryStream.size(), len);
      memoryStream.write(b, off, size);
      if (size < len) {
        ensureFile().write(b, off + size, len - size);
      }
    } else {
      ensureFile().write(b, off, len);
    }
    totalSize += len;
  }

  @NotNull
  public InputStream toInputStream() throws IOException {
    if (fileOutputStream != null) {
      flush();
    }
    return new TemporaryInputStream(memoryStream.toByteArray(), tempFile);
  }

  @Override
  public void flush() throws IOException {
    if (fileOutputStream != null) {
      fileOutputStream.flush();
    }
  }

  @Override
  public void close() throws IOException {
    if (fileOutputStream != null) {
      fileOutputStream.close();
    }
    if (tempFile != null) {
      //noinspection ResultOfMethodCallIgnored
      tempFile.delete();
      tempFile = null;
    }
  }

  private static class TemporaryInputStream extends InputStream {
    @NotNull
    private final byte[] memoryBytes;
    @Nullable
    private final FileInputStream fileStream;
    private int offset = 0;

    private TemporaryInputStream(@NotNull byte[] memoryBytes, @Nullable File file) throws FileNotFoundException {
      this.memoryBytes = memoryBytes;
      this.fileStream = file == null ? null : new FileInputStream(file);
    }

    @Override
    public int read() throws IOException {
      if (offset < memoryBytes.length) {
        //noinspection MagicNumber
        return memoryBytes[offset++] & 0xff;
      }
      if (fileStream != null) {
        return fileStream.read();
      }
      return -1;
    }

    @Override
    public int read(@NotNull byte[] buf, int off, int len) throws IOException {
      if (len == 0) {
        return 0;
      }
      if (this.offset < memoryBytes.length) {
        final int count = Math.min(len, memoryBytes.length - this.offset);
        System.arraycopy(memoryBytes, offset, buf, off, count);
        offset += count;
        return count;
      }
      if (fileStream != null) {
        return fileStream.read(buf, off, len);
      }
      return -1;
    }

    @Override
    public void close() throws IOException {
      if (fileStream != null) {
        fileStream.close();
      }
    }
  }
}
