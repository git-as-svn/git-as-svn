/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.filter;

import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mapdb.DB;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import svnserver.HashHelper;
import svnserver.StringHelper;
import svnserver.repository.git.GitObject;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Helper for common filter functionality.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitFilterHelper {
  private static final int BUFFER_SIZE = 32 * 1024;

  private GitFilterHelper() {
  }

  public static long getSize(@NotNull GitFilter filter, @Nullable Map<String, String> cacheMd5, @NotNull Map<String, Long> cacheSize, @NotNull GitObject<? extends ObjectId> objectId) throws IOException {
    final Long size = cacheSize.get(objectId.getObject().name());
    if (size != null) {
      return size;
    }
    return createMetadata(objectId, filter, cacheMd5, cacheSize).size;
  }

  @NotNull
  private static Metadata createMetadata(@NotNull GitObject<? extends ObjectId> objectId, @NotNull GitFilter filter, @Nullable Map<String, String> cacheMd5, @Nullable Map<String, Long> cacheSize) throws IOException {
    final byte[] buffer = new byte[BUFFER_SIZE];
    try (final InputStream stream = filter.inputStream(objectId)) {
      final MessageDigest digest = cacheMd5 != null ? HashHelper.md5() : null;
      long totalSize = 0;
      while (true) {
        int bytes = stream.read(buffer);
        if (bytes <= 0) break;
        if (digest != null) {
          digest.update(buffer, 0, bytes);
        }
        totalSize += bytes;
      }
      final String md5;
      if ((cacheMd5 != null) && (digest != null)) {
        md5 = StringHelper.toHex(digest.digest());
        cacheMd5.putIfAbsent(objectId.getObject().name(), md5);
      } else {
        md5 = null;
      }
      if (cacheSize != null) {
        cacheSize.putIfAbsent(objectId.getObject().name(), totalSize);
      }
      return new Metadata(totalSize, md5);
    }
  }

  @NotNull
  public static String getMd5(@NotNull GitFilter filter, @NotNull Map<String, String> cacheMd5, @Nullable Map<String, Long> cacheSize, @NotNull GitObject<? extends ObjectId> objectId) throws IOException {
    final String md5 = cacheMd5.get(objectId.getObject().name());
    if (md5 != null) {
      return md5;
    }
    //noinspection ConstantConditions
    return createMetadata(objectId, filter, cacheMd5, cacheSize).md5;
  }

  @NotNull
  public static HTreeMap<String, String> getCacheMd5(@NotNull GitFilter filter, @NotNull DB cacheDb) {
    return cacheDb.hashMap("cache.filter." + filter.getName() + ".md5", Serializer.STRING, Serializer.STRING).createOrOpen();
  }

  @NotNull
  public static HTreeMap<String, Long> getCacheSize(@NotNull GitFilter filter, @NotNull DB cacheDb) {
    return cacheDb.hashMap("cache.filter." + filter.getName() + ".size", Serializer.STRING, Serializer.LONG).createOrOpen();
  }

  private static class Metadata {
    private final long size;
    @Nullable
    private final String md5;

    private Metadata(long size, @Nullable String md5) {
      this.size = size;
      this.md5 = md5;
    }
  }
}
