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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Local storage writer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class LfsLocalReader implements LfsReader {
  @NotNull
  private final Path file;
  private final boolean compressed;
  @NotNull
  private final Map<String, String> meta;

  private LfsLocalReader(@NotNull Map<String, String> meta, @NotNull Path file, boolean compressed) {
    this.meta = meta;
    this.file = file;
    this.compressed = compressed;
  }

  @Nullable
  public static LfsLocalReader create(@NotNull LocalLfsConfig.LfsLayout layout, @NotNull Path dataRoot, @Nullable Path metaRoot, @NotNull String oid) throws IOException {
    final Map<String, String> meta;

    final Path dataPath = LfsLocalStorage.getPath(layout, dataRoot, oid, "");

    if (metaRoot != null) {
      final Path metaPath = LfsLocalStorage.getPath(layout, metaRoot, oid, ".meta");
      if (metaPath == null)
        return null;

      try (InputStream stream = Files.newInputStream(metaPath)) {
        meta = Pointer.parsePointer(IOUtils.toByteArray(stream));
      } catch (NoSuchFileException ignored) {
        return null;
      }

      if (meta == null)
        throw new IOException("Corrupt meta file: " + metaPath);

      if (!meta.get(Constants.OID).equals(oid)) {
        throw new IOException("Corrupt meta file: " + metaPath + " - unexpected oid:" + meta.get(Constants.OID));
      }
      final Path gzipPath = LfsLocalStorage.getPath(layout, dataRoot, oid, ".gz");

      if (gzipPath != null && Files.exists(gzipPath))
        return new LfsLocalReader(meta, gzipPath, true);

    } else {
      if (dataPath == null || !Files.isRegularFile(dataPath))
        return null;

      meta = new HashMap<>();
      meta.put(Constants.OID, oid);
      meta.put(Constants.SIZE, Long.toString(Files.size(dataPath)));
    }

    if (dataPath != null && Files.isRegularFile(dataPath))
      return new LfsLocalReader(meta, dataPath, false);

    return null;
  }

  @NotNull
  @Override
  public InputStream openStream() throws IOException {
    final InputStream result = Files.newInputStream(file);
    return compressed ? new GZIPInputStream(result) : result;
  }

  @Nullable
  @Override
  public InputStream openGzipStream() throws IOException {
    if (!compressed)
      return null;

    return Files.newInputStream(file);
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
