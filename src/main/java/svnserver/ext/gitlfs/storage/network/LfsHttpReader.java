/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.network;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.gitlfs.client.Client;
import ru.bozaro.gitlfs.common.data.BatchItem;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Network storage reader.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
final class LfsHttpReader implements LfsReader {
  @NotNull
  private final Client lfsClient;
  @NotNull
  private final BatchItem item;

  LfsHttpReader(@NotNull Client lfsClient, @NotNull BatchItem item) {
    this.lfsClient = lfsClient;
    this.item = item;
  }

  @NotNull
  @Override
  public InputStream openStream() throws IOException {
    return lfsClient.openObject(item, item);
  }

  @Nullable
  @Override
  public InputStream openGzipStream() {
    return null;
  }

  @Override
  public long getSize() {
    return item.getSize();
  }

  @Nullable
  @Override
  public String getMd5() {
    return null;
  }

  @NotNull
  @Override
  public String getOid(boolean hashOnly) {
    return hashOnly ? item.getOid().substring(LfsStorage.OID_PREFIX.length()) : item.getOid();
  }
}
