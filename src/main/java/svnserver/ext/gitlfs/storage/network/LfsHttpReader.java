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
import ru.bozaro.gitlfs.client.exceptions.UnauthorizedException;
import ru.bozaro.gitlfs.common.data.Links;
import ru.bozaro.gitlfs.common.data.Meta;
import ru.bozaro.gitlfs.common.data.ObjectRes;
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
  private final Meta meta;
  @NotNull
  private Links links;

  public LfsHttpReader(@NotNull LfsHttpStorage owner, @NotNull Meta meta, @NotNull Links links) throws IOException {
    this.owner = owner;
    this.links = links;
    this.meta = meta;
  }

  @NotNull
  @Override
  public InputStream openStream() throws IOException {
    for (int pass = 0; ; ++pass) {
      try {
        return owner.getObject(links);
      } catch (UnauthorizedException e) {
        if (pass != 0) throw e;
        owner.invalidate(User.getAnonymous());
        final ObjectRes newMeta = owner.getMeta(meta.getOid());
        if (newMeta != null) {
          this.links = newMeta;
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
    return meta.getSize();
  }

  @Nullable
  @Override
  public String getMd5() {
    return null;
  }

  @NotNull
  @Override
  public String getOid(boolean hashOnly) {
    return hashOnly ? meta.getOid().substring(LfsStorage.OID_PREFIX.length()) : meta.getOid();
  }
}
