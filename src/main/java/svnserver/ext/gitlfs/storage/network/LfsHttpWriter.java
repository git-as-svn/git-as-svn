/**
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
import svnserver.HashHelper;
import svnserver.TemporaryOutputStream;
import svnserver.auth.User;
import svnserver.ext.gitlfs.storage.LfsWriter;
import svnserver.ext.gitlfs.storage.local.LfsLocalStorage;

import java.io.IOException;
import java.security.MessageDigest;

/**
 * Network storage writer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsHttpWriter extends LfsWriter {
  @NotNull
  private final LfsHttpStorage owner;
  @NotNull
  private final User user;
  @NotNull
  private final TemporaryOutputStream content;
  @NotNull
  private final MessageDigest digestSha;

  public LfsHttpWriter(@NotNull LfsHttpStorage owner, @NotNull User user) {
    this.owner = owner;
    this.user = user;
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
    owner.putObject(user, content::toInputStream, sha, content.size());
    return oid;
  }
}
