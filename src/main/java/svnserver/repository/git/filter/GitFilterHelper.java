/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.filter;

import org.atteo.classindex.ClassIndex;
import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mapdb.DB;
import org.tmatesoft.svn.core.SVNException;
import svnserver.StringHelper;
import svnserver.repository.git.GitObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper for common filter functionality.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitFilterHelper {
  private static final int BUFFER_SIZE = 32 * 1024;

  private GitFilterHelper() {
  }

  public static long getSize(@NotNull GitFilter filter, @NotNull DB cacheDb, @NotNull GitObject<? extends ObjectId> objectId, boolean needMd5) throws IOException, SVNException {
    final Map<String, Long> cacheSize = getCacheSize(filter, cacheDb);
    final Long size = cacheSize.get(objectId.getObject().name());
    if (size != null) {
      return size;
    }
    return createMetadata(objectId, filter, needMd5 ? getCacheMd5(filter, cacheDb) : null, cacheSize).size;
  }

  @NotNull
  public static String getMd5(@NotNull GitFilter filter, @NotNull DB cacheDb, @NotNull GitObject<? extends ObjectId> objectId, boolean needSize) throws IOException, SVNException {
    final Map<String, String> cacheMd5 = getCacheMd5(filter, cacheDb);
    final String md5 = cacheMd5.get(objectId.getObject().name());
    if (md5 != null) {
      return md5;
    }
    //noinspection ConstantConditions
    return createMetadata(objectId, filter, cacheMd5, needSize ? getCacheSize(filter, cacheDb) : null).md5;
  }

  @NotNull
  private static Map<String, String> getCacheMd5(@NotNull GitFilter filter, @NotNull DB cacheDb) {
    return cacheDb.getHashMap("cache.filter." + filter.getName() + ".md5");
  }

  @NotNull
  private static Map<String, Long> getCacheSize(@NotNull GitFilter filter, @NotNull DB cacheDb) {
    return cacheDb.getHashMap("cache.filter." + filter.getName() + ".size");
  }

  @NotNull
  private static Metadata createMetadata(@NotNull GitObject<? extends ObjectId> objectId, @NotNull GitFilter filter, @Nullable Map<String, String> cacheMd5, @Nullable Map<String, Long> cacheSize) throws IOException, SVNException {
    final byte[] buffer = new byte[BUFFER_SIZE];
    try (final InputStream stream = filter.inputStream(objectId)) {
      final MessageDigest digest = cacheMd5 != null ? createDigestMd5() : null;
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

  private static MessageDigest createDigestMd5() {
    try {
      return MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @NotNull
  public static Map<String, GitFilter> createFilters(@NotNull DB cacheDb) {
    final Map<String, GitFilter> result = new HashMap<>();
    for (Class<? extends GitFilter> type : ClassIndex.getSubclasses(GitFilter.class)) {
      if (Modifier.isAbstract(type.getModifiers())) continue;
      if (!Modifier.isPublic(type.getModifiers())) continue;
      try {
        final GitFilter filter = type.getConstructor(DB.class).newInstance(cacheDb);
        final GitFilter oldFilter = result.put(filter.getName(), filter);
        if (oldFilter != null) {
          throw new RuntimeException("Found two classes mapped for same file: " + oldFilter.getClass() + " and " + type);
        }
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
    }
    return result;
  }

  private static class Metadata {
    private final long size;
    @Nullable
    private final String md5;

    public Metadata(long size, @Nullable String md5) {
      this.size = size;
      this.md5 = md5;
    }
  }
}
