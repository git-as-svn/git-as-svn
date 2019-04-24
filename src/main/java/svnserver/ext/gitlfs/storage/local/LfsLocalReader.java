/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.local;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.gitlfs.pointer.Constants;
import ru.bozaro.gitlfs.pointer.Pointer;
import svnserver.ext.gitlfs.config.LocalLfsConfig;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Local storage writer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsLocalReader implements LfsReader {
  @Nullable
  private final File dataPath;
  @Nullable
  private final File gzipPath;
  @NotNull
  private final Map<String, String> meta;

  private LfsLocalReader(@NotNull Map<String, String> meta, @Nullable File dataPath, @Nullable File gzipPath) {
    this.meta = meta;
    this.dataPath = dataPath;
    this.gzipPath = gzipPath;
  }

  @Nullable
  public static LfsLocalReader create(@NotNull LocalLfsConfig.LfsLayout layout, @NotNull File dataRoot, @Nullable File metaRoot, @NotNull String oid) throws IOException {
    final Map<String, String> meta;
    File dataPath = LfsLocalStorage.getPath(layout, dataRoot, oid, "");
    File gzipPath = LfsLocalStorage.getPath(layout, dataRoot, oid, ".gz");
    if (metaRoot != null) {
      final File metaPath = LfsLocalStorage.getPath(layout, metaRoot, oid, ".meta");
      if (metaPath == null || !metaPath.isFile()) {
        return null;
      }
      try (InputStream stream = new FileInputStream(metaPath)) {
        meta = Pointer.parsePointer(IOUtils.toByteArray(stream));
      }
      if (meta == null) {
        throw new IOException("Corrupted meta file: " + metaPath.getAbsolutePath());
      }
      if (!meta.get(Constants.OID).equals(oid)) {
        throw new IOException("Corrupted meta file: " + metaPath.getAbsolutePath() + " - unexpected oid:" + meta.get(Constants.OID));
      }
    } else {
      if (dataPath == null || !dataPath.isFile()) {
        return null;
      }
      gzipPath = null;
      meta = new HashMap<>();
      meta.put(Constants.OID, oid);
      meta.put(Constants.SIZE, Long.toString(dataPath.length()));
    }

    if (dataPath != null && !dataPath.isFile()) {
      dataPath = null;
    }
    if (gzipPath != null && !gzipPath.isFile()) {
      gzipPath = null;
    }
    if (dataPath == null && gzipPath == null) return null;
    return new LfsLocalReader(meta, dataPath, gzipPath);
  }

  @NotNull
  @Override
  public InputStream openStream() throws IOException {
    if (dataPath != null) {
      return new FileInputStream(dataPath);
    }
    if (gzipPath != null) {
      return new GZIPInputStream(new FileInputStream(gzipPath));
    }
    throw new IllegalStateException();
  }

  @Nullable
  @Override
  public InputStream openGzipStream() throws IOException {
    return gzipPath != null ? new FileInputStream(gzipPath) : null;
  }

  @Override
  public long getSize() {
    return Long.parseLong(meta.get(Constants.SIZE));
  }

  @Nullable
  @Override
  public String getMd5() {
    return meta.get(LfsLocalStorage.HASH_MD5);
  }

  @NotNull
  @Override
  public String getOid(boolean hashOnly) {
    final String oid = meta.get(Constants.OID);
    return hashOnly ? oid.substring(LfsStorage.OID_PREFIX.length()) : oid;
  }
}
