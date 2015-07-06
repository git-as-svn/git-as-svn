/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.filter;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.tmatesoft.svn.core.SVNException;
import svnserver.repository.git.GitObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.nio.charset.StandardCharsets;

/**
 * Get object for symbolic link.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitFilterLink implements GitFilter {
  @NotNull
  private static final byte[] LINK_PREFIX = "link ".getBytes(StandardCharsets.ISO_8859_1);
  @NotNull
  public static final String NAME = "link";
  @NotNull
  private final DB cacheDb;

  public GitFilterLink(@NotNull DB cacheDb) {
    this.cacheDb = cacheDb;
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }

  @NotNull
  @Override
  public String getMd5(@NotNull GitObject<? extends ObjectId> objectId) throws IOException, SVNException {
    return GitFilterHelper.getMd5(this, cacheDb, objectId, false);
  }

  @Override
  public long getSize(@NotNull GitObject<? extends ObjectId> objectId) throws IOException, SVNException {
    final ObjectReader reader = objectId.getRepo().newObjectReader();
    return reader.getObjectSize(objectId.getObject(), Constants.OBJ_BLOB) + LINK_PREFIX.length;
  }

  @NotNull
  @Override
  public InputStream inputStream(@NotNull GitObject<? extends ObjectId> objectId) throws IOException {
    return new InputWrapper(objectId.openObject().openStream());
  }

  @NotNull
  @Override
  public OutputStream outputStream(@NotNull OutputStream stream) {
    return new OutputWrapper(stream);
  }

  private final static class InputWrapper extends InputStream {
    @NotNull
    private final InputStream inner;
    private int offset = 0;

    public InputWrapper(@NotNull InputStream inner) {
      this.inner = inner;
    }

    @Override
    public int read() throws IOException {
      if (offset >= LINK_PREFIX.length) {
        return inner.read();
      }
      return LINK_PREFIX[offset++] & 0xFF;
    }

    @Override
    public int read(@NotNull byte[] buffer, int off, int len) throws IOException {
      if (offset >= LINK_PREFIX.length) {
        return super.read(buffer, off, len);
      }
      final int size = Math.min(len, LINK_PREFIX.length - offset);
      System.arraycopy(LINK_PREFIX, 0, buffer, off, size);
      offset += size;
      if (size < len) {
        int bytes = inner.read(buffer, off + size, len - size);
        if (bytes > 0) {
          return size + bytes;
        }
      }
      return size;
    }

    @Override
    public void close() throws IOException {
      inner.close();
    }
  }

  private final static class OutputWrapper extends OutputStream {
    @NotNull
    private final OutputStream inner;
    private int offset = 0;

    public OutputWrapper(@NotNull OutputStream inner) {
      this.inner = inner;
    }

    @Override
    public void write(int b) throws IOException {
      if (offset >= LINK_PREFIX.length) {
        inner.write(b);
        return;
      }
      if ((LINK_PREFIX[offset++] & 0xFF) != b) {
        throw new StreamCorruptedException("Link entry has invalid content prefix.");
      }
    }

    @Override
    public void write(@NotNull byte[] buffer, int off, int len) throws IOException {
      if (offset >= LINK_PREFIX.length) {
        super.write(buffer, off, len);
        return;
      }
      final int size = Math.min(len, LINK_PREFIX.length - offset);
      for (int i = 0; i < size; ++i) {
        if ((LINK_PREFIX[offset + i] & 0xFF) != buffer[off + i]) {
          throw new StreamCorruptedException("Link entry has invalid content prefix.");
        }
      }
      offset += size;
      if (size < len) {
        inner.write(buffer, off + size, len - size);
      }
    }

    @Override
    public void flush() throws IOException {
      inner.flush();
    }

    @Override
    public void close() throws IOException {
      inner.close();
    }
  }
}
