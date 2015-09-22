/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.local;

import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.gitlfs.pointer.Constants;
import ru.bozaro.gitlfs.pointer.Pointer;
import svnserver.DateHelper;
import svnserver.HashHelper;
import svnserver.auth.User;
import svnserver.ext.gitlfs.storage.LfsWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * Local storage writer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsLocalWriter extends LfsWriter {
  @NotNull
  private final File dataRoot;
  @NotNull
  private final File metaRoot;
  @NotNull
  private final File dataTemp;
  @NotNull
  private final File metaTemp;
  private final boolean compress;
  @Nullable
  private final User user;

  @Nullable
  private OutputStream dataStream;
  @NotNull
  private final MessageDigest digestMd5;
  @NotNull
  private final MessageDigest digestSha;
  private long size;

  public LfsLocalWriter(@NotNull File dataRoot, @NotNull File metaRoot, boolean compress, @Nullable User user) throws IOException {
    this.dataRoot = dataRoot;
    this.metaRoot = metaRoot;
    this.compress = compress;
    this.user = user;

    final String prefix = UUID.randomUUID().toString();
    dataTemp = new File(new File(dataRoot, "tmp"), prefix + ".tmp");
    //noinspection ResultOfMethodCallIgnored
    dataTemp.getParentFile().mkdirs();
    dataTemp.deleteOnExit();

    metaTemp = new File(new File(metaRoot, "tmp"), prefix + ".tmp");

    digestMd5 = HashHelper.md5();
    digestSha = HashHelper.sha256();
    size = 0;
    if (compress) {
      dataStream = new GZIPOutputStream(new FileOutputStream(dataTemp));
    } else {
      dataStream = new FileOutputStream(dataTemp);
    }
  }

  @Override
  public void write(int b) throws IOException {
    if (dataStream == null) {
      throw new IllegalStateException();
    }
    dataStream.write(b);
    digestMd5.update((byte) b);
    digestSha.update((byte) b);
    size += 1;
  }

  @Override
  public void write(@NotNull byte[] b, int off, int len) throws IOException {
    if (dataStream == null) {
      throw new IllegalStateException();
    }
    dataStream.write(b, off, len);
    digestMd5.update(b, off, len);
    digestSha.update(b, off, len);
    size += len;
  }

  @NotNull
  @Override
  public String finish(@Nullable String expectedOid) throws IOException {
    if (dataStream == null) {
      throw new IllegalStateException();
    }
    dataStream.close();
    dataStream = null;

    final byte[] sha = digestSha.digest();
    final byte[] md5 = digestMd5.digest();

    final String oid = LfsLocalStorage.OID_PREFIX + Hex.encodeHexString(sha);
    if (expectedOid != null && !expectedOid.equals(oid)) {
      throw new IOException("Invalid stream checksum: expected " + expectedOid + ", but actual " + oid);
    }

    // Write file data
    final File dataPath = LfsLocalStorage.getPath(dataRoot, oid, compress ? ".gz" : "");
    if (dataPath == null) {
      throw new IllegalStateException();
    }
    //noinspection ResultOfMethodCallIgnored
    dataPath.getParentFile().mkdirs();
    if (!dataTemp.renameTo(dataPath) && !dataPath.isFile()) {
      throw new IOException("Can't rename file: " + dataTemp.getPath() + " -> " + dataPath.getPath());
    }
    //noinspection ResultOfMethodCallIgnored
    dataTemp.delete();

    // Write metadata
    final File metaPath = LfsLocalStorage.getPath(metaRoot, oid, ".meta");
    if (metaPath == null) {
      throw new IllegalStateException();
    }
    if (!metaPath.exists()) {
      //noinspection ResultOfMethodCallIgnored
      metaPath.getParentFile().mkdirs();
      //noinspection ResultOfMethodCallIgnored
      metaTemp.getParentFile().mkdirs();

      try (FileOutputStream stream = new FileOutputStream(metaTemp)) {
        final Map<String, String> map = new HashMap<>();
        map.put(Constants.SIZE, String.valueOf(size));
        map.put(Constants.OID, oid);
        map.put(LfsLocalStorage.HASH_MD5, Hex.encodeHexString(md5));
        map.put(LfsLocalStorage.CREATE_TIME, DateHelper.toISO8601(Instant.now()));
        if ((user != null) && (!user.isAnonymous())) {
          if (user.getEmail() != null) {
            map.put(LfsLocalStorage.META_EMAIL, user.getEmail());
          }
          map.put(LfsLocalStorage.META_USER_NAME, user.getUserName());
          map.put(LfsLocalStorage.META_REAL_NAME, user.getRealName());
        }
        stream.write(Pointer.serializePointer(map));
        stream.close();
        if (!metaTemp.renameTo(metaPath) && !metaPath.isFile()) {
          throw new IOException("Can't rename file: " + metaTemp.getPath() + " -> " + metaPath.getPath());
        }
      } finally {
        //noinspection ResultOfMethodCallIgnored
        metaTemp.delete();
      }
    }
    return oid;
  }

  @Override
  public void close() throws IOException {
    if (dataStream != null) {
      dataStream.close();
      dataStream = null;
      //noinspection ResultOfMethodCallIgnored
      dataTemp.delete();
    }
  }
}
