/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.network;

import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.gitlfs.client.Client;
import ru.bozaro.gitlfs.common.data.*;
import svnserver.HashHelper;
import svnserver.TemporaryOutputStream;
import svnserver.ext.gitlfs.storage.LfsWriter;
import svnserver.ext.gitlfs.storage.local.LfsLocalStorage;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Collections;

/**
 * Network storage writer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class LfsHttpWriter extends LfsWriter {
  @NotNull
  private final Client lfsClient;
  @NotNull
  private final TemporaryOutputStream content;
  @NotNull
  private final MessageDigest digestSha;

  LfsHttpWriter(@NotNull Client lfsClient) {
    this.lfsClient = lfsClient;
    this.digestSha = HashHelper.sha256();
    this.content = new TemporaryOutputStream();
  }

  @Override
  public void write(int b) throws IOException {
    content.write(b);
    digestSha.update((byte) b);
  }

  @Override
  public void write(@NotNull byte[] buffer, int off, int len) throws IOException {
    content.write(buffer, off, len);
    digestSha.update(buffer, off, len);
  }

  @NotNull
  @Override
  public String finish(@Nullable String expectedOid) throws IOException {
    final String sha = Hex.encodeHexString(digestSha.digest());
    final String oid = LfsLocalStorage.OID_PREFIX + sha;
    if (expectedOid != null && !expectedOid.equals(oid)) {
      throw new IOException("Invalid stream checksum: expected " + expectedOid + ", but actual " + LfsLocalStorage.OID_PREFIX + sha);
    }

    final BatchRes batchRes = lfsClient.postBatch(new BatchReq(Operation.Upload, Collections.singletonList(new Meta(sha, content.size()))));
    if (batchRes.getObjects().isEmpty())
      throw new IOException(String.format("Empty batch response while uploading %s", sha));

    for (BatchItem batchItem : batchRes.getObjects()) {
      if (batchItem.getError() != null)
        throw new IOException(String.format("LFS error[%s]: %s", batchItem.getError().getCode(), batchItem.getError().getMessage()));

      lfsClient.putObject(content::toInputStream, batchItem, batchItem);
    }

    return oid;
  }
}
