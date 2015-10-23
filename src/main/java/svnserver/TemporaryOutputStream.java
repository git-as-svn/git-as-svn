/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver;

import com.google.common.io.ByteStreams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stream for write-then-read functionality.
 *
 * @author Artem V. Navrotskiy
 */
public class TemporaryOutputStream extends OutputStream {
  public interface Holder extends AutoCloseable {
    @NotNull
    Holder copy();

    @Override
    void close() throws IOException;
  }

  private interface CloseAction extends AutoCloseable {
    @Override
    void close() throws IOException;
  }

  @SuppressWarnings("MagicNumber")
  private static final int MAX_MEMORY_SIZE = 8 * 1024 * 1024;

  private final int maxMemorySize;
  @NotNull
  private final ByteArrayOutputStream memoryStream = new ByteArrayOutputStream();
  @NotNull
  private final Holder holder;
  @Nullable
  private File file;
  @Nullable
  private FileOutputStream fileOutputStream;
  private long totalSize = 0;

  public TemporaryOutputStream() {
    this(MAX_MEMORY_SIZE);
  }

  public TemporaryOutputStream(@NotNull InputStream stream) throws IOException {
    this(MAX_MEMORY_SIZE);
    ByteStreams.copy(stream, this);
  }

  public TemporaryOutputStream(int maxMemorySize) {
    this.maxMemorySize = maxMemorySize;
    this.holder = new FileHolder(this::cleanup);
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

  public Holder holder() {
    return holder.copy();
  }

  @TestOnly
  @Nullable
  File tempFile() {
    return file;
  }

  @NotNull
  private FileOutputStream ensureFile() throws IOException {
    if (fileOutputStream == null) {
      file = File.createTempFile("tmp", "");
      file.deleteOnExit();
      fileOutputStream = new FileOutputStream(file);
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
    if (file != null) {
      return new TemporaryInputStream(memoryStream.toByteArray(), file, holder);
    } else {
      return new ByteArrayInputStream(memoryStream.toByteArray());
    }
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
    holder.close();
  }

  private void cleanup() throws IOException {
    if (file != null && !file.delete()) {
      throw new IOException("Can't delete temporary file: " + file.getAbsolutePath());
    }
  }

  private static class TemporaryInputStream extends InputStream {
    @NotNull
    private final byte[] memoryBytes;
    @NotNull
    private final FileInputStream fileStream;
    @NotNull
    private final Holder holder;
    private int offset = 0;

    private TemporaryInputStream(@NotNull byte[] memoryBytes, @NotNull File file, @NotNull Holder holder) throws FileNotFoundException {
      this.memoryBytes = memoryBytes;
      this.holder = holder.copy();
      this.fileStream = new FileInputStream(file);
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
      fileStream.close();
      holder.close();
    }
  }

  private static class FileHolder implements Holder {
    @NotNull
    private final CloseAction action;
    @NotNull
    private final AtomicInteger usages;
    @NotNull
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public FileHolder(@NotNull CloseAction action) {
      this(action, new AtomicInteger(1));
    }

    private FileHolder(@NotNull CloseAction action, @NotNull AtomicInteger usages) {
      this.action = action;
      this.usages = usages;
    }

    @NotNull
    public FileHolder copy() {
      usages.incrementAndGet();
      return new FileHolder(action, usages);
    }

    @Override
    public void close() throws IOException {
      if (closed.compareAndSet(false, true)) {
        if (usages.decrementAndGet() == 0) {
          action.close();
        }
      }
    }
  }
}
