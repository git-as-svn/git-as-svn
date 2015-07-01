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
import svnserver.SvnConstants;
import svnserver.repository.git.GitObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Get object for symbolic link.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitFilterLink implements GitFilter {
  @NotNull
  private final DB cacheDb;

  public GitFilterLink(@NotNull DB cacheDb) {
    this.cacheDb = cacheDb;
  }

  @NotNull
  @Override
  public String getName() {
    return "link";
  }

  @NotNull
  @Override
  public InputStream openStream(@NotNull GitObject<ObjectId> objectId) throws IOException {
    return new StreamWrapper(objectId.openObject().openStream());
  }

  @NotNull
  @Override
  public String getMd5(@NotNull GitObject<ObjectId> objectId) throws IOException, SVNException {
    return GitFilterHelper.getMd5(this, cacheDb, objectId, false);
  }

  @Override
  public long getSize(@NotNull GitObject<ObjectId> objectId) throws IOException, SVNException {
    final ObjectReader reader = objectId.getRepo().newObjectReader();
    return reader.getObjectSize(objectId.getObject(), Constants.OBJ_BLOB) + SvnConstants.LINK_PREFIX.length();
  }

  private final static class StreamWrapper extends InputStream {
    @NotNull
    private static final byte[] LINK_PREFIX = SvnConstants.LINK_PREFIX.getBytes(StandardCharsets.ISO_8859_1);
    @NotNull
    private final InputStream inner;
    private int offset = 0;

    public StreamWrapper(@NotNull InputStream inner) {
      this.inner = inner;
    }

    @Override
    public int read() throws IOException {
      if (offset >= LINK_PREFIX.length) {
        return inner.read();
      }
      return LINK_PREFIX[offset++];
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
}
