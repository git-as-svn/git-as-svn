/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.network;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.gitlfs.common.client.exceptions.UnauthorizedException;
import ru.bozaro.gitlfs.common.data.Meta;
import svnserver.auth.User;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Network storage reader.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsHttpReader implements LfsReader {
  @NotNull
  private final LfsHttpStorage owner;
  @NotNull
  private final String oid;
  private final long size;
  @NotNull
  private Meta meta;

  public LfsHttpReader(@NotNull LfsHttpStorage owner, @NotNull Meta meta) throws IOException {
    this.owner = owner;
    this.meta = meta;
    final Long size = meta.getSize();
    if (size == null) {
      throw new IOException("Metadata doesn't contains size data");
    }
    final String oid = meta.getOid();
    if (oid == null) {
      throw new IOException("Metadata doesn't contains object hash");
    }
    this.size = size;
    this.oid = oid;
  }

  @NotNull
  @Override
  public InputStream openStream() throws IOException {
    for (int pass = 0; ; ++pass) {
      try {
        return owner.getObject(meta);
      } catch (UnauthorizedException e) {
        if (pass != 0) throw e;
        owner.invalidate(User.getAnonymous());
        final Meta newMeta = owner.getMeta(oid);
        if (newMeta != null) {
          this.meta = newMeta;
        }
      }
    }
  }

  @Nullable
  @Override
  public InputStream openGzipStream() throws IOException {
    return null;
  }

  @Override
  public long getSize() {
    return size;
  }

  @Nullable
  @Override
  public String getMd5() {
    return null;
  }

  @NotNull
  @Override
  public String getOid(boolean hashOnly) {
    return hashOnly ? oid.substring(LfsStorage.OID_PREFIX.length()) : oid;
  }
}
