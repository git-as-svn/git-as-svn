/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;

/**
 * Stream for write-then-read functionality.
 *
 * @author Artem V. Navrotskiy
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class TemporaryOutputStream extends OutputStream {
  @SuppressWarnings("MagicNumber")
  private static final int MAX_MEMORY_SIZE = 8 * 1024 * 1024;
  private final int maxMemorySize;
  @NotNull
  private final ByteArrayOutputStream memoryStream = new ByteArrayOutputStream();
  @Nullable
  private File file;
  @Nullable
  private FileOutputStream fileOutputStream;
  private long totalSize = 0;
  private boolean closed;

  public TemporaryOutputStream() {
    this(MAX_MEMORY_SIZE);
  }

  public TemporaryOutputStream(int maxMemorySize) {
    this.maxMemorySize = maxMemorySize;
  }

  public TemporaryOutputStream(@NotNull InputStream stream) throws IOException {
    this(MAX_MEMORY_SIZE);
    IOUtils.copy(stream, this);
  }

  @Override
  public void write(int b) throws IOException {
    if (closed)
      throw new IOException();

    if (memoryStream.size() < maxMemorySize) {
      memoryStream.write(b);
      totalSize++;
      return;
    }
    ensureFile().write(b);
    totalSize++;
  }

  @NotNull
  private FileOutputStream ensureFile() throws IOException {
    if (fileOutputStream == null) {
      file = File.createTempFile("tmp", "git-as-svn");
      fileOutputStream = new FileOutputStream(file);
    }
    return fileOutputStream;
  }

  @Override
  public void write(@NotNull byte[] b, int off, int len) throws IOException {
    if (closed)
      throw new IOException();

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

  @Override
  public void flush() throws IOException {
    if (closed)
      throw new IOException();

    if (fileOutputStream != null)
      fileOutputStream.flush();
  }

  @Override
  public void close() throws IOException {
    if (closed)
      return;

    closed = true;

    try {
      if (fileOutputStream != null)
        fileOutputStream.close();
    } finally {
      if (file != null)
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
  }

  public long size() {
    return totalSize;
  }

  @TestOnly
  @Nullable
  File tempFile() {
    return file;
  }

  @NotNull
  public InputStream toInputStream() throws IOException {
    if (fileOutputStream != null)
      flush();

    final InputStream result = file == null
        ? new ByteArrayInputStream(memoryStream.toByteArray())
        : new TemporaryInputStream(memoryStream.toByteArray(), file);

    file = null;
    close();

    return result;
  }

  private static class TemporaryInputStream extends InputStream {
    @NotNull
    private final byte[] memoryBytes;
    @NotNull
    private final FileInputStream fileStream;
    @NotNull
    private final File file;
    private int offset = 0;

    private TemporaryInputStream(@NotNull byte[] memoryBytes, @NotNull File file) throws FileNotFoundException {
      this.memoryBytes = memoryBytes;
      this.fileStream = new FileInputStream(file);
      this.file = file;
    }

    @Override
    public int read() throws IOException {
      if (offset < memoryBytes.length) {
        //noinspection MagicNumber
        return memoryBytes[offset++] & 0xff;
      }
      return fileStream.read();
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
      return fileStream.read(buf, off, len);
    }

    @Override
    public void close() throws IOException {
      try {
        fileStream.close();
      } finally {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
      }
    }
  }
}
