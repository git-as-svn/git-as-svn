/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.filter;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.tmatesoft.svn.core.SVNException;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.config.LfsConfig;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsWriter;
import svnserver.repository.git.GitObject;
import svnserver.repository.git.filter.GitFilter;
import svnserver.repository.git.filter.GitFilterHelper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Filter for Git LFS.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsFilter implements GitFilter {
  @NotNull
  public static final String NAME = "lfs";
  @NotNull
  private final LfsStorage storage;
  @NotNull
  private final DB cacheDb;

  public LfsFilter(@NotNull SharedContext context) throws IOException, SVNException {
    this.storage = LfsConfig.getStorage(context);
    this.cacheDb = context.getCacheDB();
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }

  @NotNull
  @Override
  public String getMd5(@NotNull GitObject<? extends ObjectId> objectId) throws IOException, SVNException {
    final ObjectLoader loader = objectId.openObject();
    final ObjectStream stream = loader.openStream();
    final byte[] header = new byte[LfsPointer.POINTER_MAX_SIZE];
    int length = IOUtils.read(stream, header);
    if (length < header.length) {
      final Map<String, String> pointer = LfsPointer.parsePointer(header, 0, length);
      if (pointer != null) {
        final LfsReader reader = storage.getReader(pointer.get(LfsPointer.OID));
        if (reader != null) {
          return reader.getMd5();
        }
      }
    }
    return GitFilterHelper.getMd5(this, cacheDb, objectId, false);
  }

  @Override
  public long getSize(@NotNull GitObject<? extends ObjectId> objectId) throws IOException, SVNException {
    final ObjectLoader loader = objectId.openObject();
    final ObjectStream stream = loader.openStream();
    final byte[] header = new byte[LfsPointer.POINTER_MAX_SIZE];
    int length = IOUtils.read(stream, header);
    if (length < header.length) {
      final Map<String, String> pointer = LfsPointer.parsePointer(header, 0, length);
      if (pointer != null) {
        final LfsReader reader = storage.getReader(pointer.get(LfsPointer.OID));
        if (reader != null) {
          return reader.getSize();
        }
      }
    }
    return loader.getSize();
  }

  @NotNull
  @Override
  public InputStream inputStream(@NotNull GitObject<? extends ObjectId> objectId) throws IOException, SVNException {
    final ObjectLoader loader = objectId.openObject();
    final ObjectStream stream = loader.openStream();
    final byte[] header = new byte[LfsPointer.POINTER_MAX_SIZE];
    int length = IOUtils.read(stream, header);
    if (length < header.length) {
      final Map<String, String> pointer = LfsPointer.parsePointer(header, 0, length);
      if (pointer != null) {
        final LfsReader reader = storage.getReader(pointer.get(LfsPointer.OID));
        if (reader != null) {
          return reader.openStream();
        }
      }
    }
    return new TemporaryInputStream(header, length, stream);
  }

  @NotNull
  @Override
  public OutputStream outputStream(@NotNull OutputStream stream) throws IOException, SVNException {
    return new TemporaryOutputStream(storage.getWriter(), stream);
  }

  private static class TemporaryInputStream extends InputStream {
    @NotNull
    private final byte[] header;
    @NotNull
    private final InputStream stream;
    private final int length;
    private int offset = 0;

    private TemporaryInputStream(@NotNull byte[] header, int length, @NotNull InputStream stream) throws FileNotFoundException {
      this.header = header;
      this.length = length;
      this.stream = stream;
    }

    @Override
    public int read() throws IOException {
      if (offset < length) {
        //noinspection MagicNumber
        return header[offset++] & 0xff;
      }
      return stream.read();
    }

    @Override
    public int read(@NotNull byte[] buf, int off, int len) throws IOException {
      if (len == 0) {
        return 0;
      }
      if (this.offset < length) {
        final int count = Math.min(len, length - this.offset);
        System.arraycopy(header, offset, buf, off, count);
        offset += count;
        return count;
      }
      return stream.read(buf, off, len);
    }

    @Override
    public void close() throws IOException {
      stream.close();
    }
  }

  private static class TemporaryOutputStream extends OutputStream {
    private final LfsWriter writer;
    private final OutputStream stream;
    private long size;

    public TemporaryOutputStream(LfsWriter writer, OutputStream stream) {
      this.writer = writer;
      this.stream = stream;
      size = 0;
    }

    @Override
    public void write(int b) throws IOException {
      writer.write(b);
      size++;
    }

    @Override
    public void write(@NotNull byte[] b, int off, int len) throws IOException {
      writer.write(b, off, len);
      size += len;
    }

    @Override
    public void flush() throws IOException {
      writer.flush();
    }

    @Override
    public void close() throws IOException {
      final Map<String, String> pointer = LfsPointer.createPointer(writer.finish(null), size);
      writer.close();
      stream.write(LfsPointer.serializePointer(pointer));
      stream.close();
    }
  }
}
