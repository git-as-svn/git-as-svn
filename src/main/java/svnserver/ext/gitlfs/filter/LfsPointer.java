/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.filter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Class for read/writer pointer blobs.
 * https://github.com/github/git-lfs/blob/master/docs/spec.md
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsPointer {
  @NotNull
  private final static String[] REQUIRED = {
      "oid",
      "size",
  };
  private final static String VERSION = "https://git-lfs.github.com/spec/v1";
  @NotNull
  private final static byte[] PREFIX = "version ".getBytes(StandardCharsets.UTF_8);

  @NotNull
  public static Map<String, String> createPointer(@NotNull String oid, long size) {
    final Map<String, String> pointer = new TreeMap<>();
    pointer.put("version", VERSION);
    pointer.put("oid", oid);
    pointer.put("size", Long.toString(size));
    return pointer;
  }

  @NotNull
  public static byte[] serializePointer(@NotNull Map<String, String> pointer) {
    final Map<String, String> data = new TreeMap<>(pointer);
    final StringBuilder buffer = new StringBuilder();
    // Write version.
    {
      String version = data.remove("version");
      if (version == null) {
        version = VERSION;
      }
      buffer.append("version").append(' ').append(version).append('\n');
    }
    for (Map.Entry<String, String> entry : data.entrySet()) {
      buffer.append(entry.getKey()).append(' ').append(entry.getValue()).append('\n');
    }
    return buffer.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Read pointer data.
   *
   * @param blob Blob data.
   * @return Return pointer info or null if blob is not a pointer data.
   */
  @Nullable
  public static Map<String, String> parsePointer(@NotNull byte[] blob) {
    // Check prefix
    if (blob.length < PREFIX.length) return null;
    for (int i = 0; i < PREFIX.length; ++i) {
      if (blob[i] != PREFIX[i]) return null;
    }
    // Reading key value pairs
    final TreeMap<String, String> result = new TreeMap<>();
    final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
    String lastKey = null;
    int keyOffset = 0;
    int required = 0;
    while (true) {
      if (keyOffset >= blob.length) {
        break;
      }
      int valueOffset = keyOffset;
      // Key
      while (true) {
        valueOffset++;
        if (valueOffset < blob.length) {
          final byte c = blob[valueOffset];
          if (c == ' ') break;
          // Keys MUST only use the characters [a-z] [0-9] . -.
          if (c >= 'a' && c <= 'z') continue;
          if (c >= '0' && c <= '9') continue;
          if (c == '.' || c == '-') continue;
        }
        // Found invalid character.
        return null;
      }
      int endOffset = valueOffset;
      // Value
      while (true) {
        endOffset++;
        if (endOffset >= blob.length) return null;
        // Values MUST NOT contain return or newline characters.
        if (blob[endOffset] == '\n') break;
      }
      final String key = new String(blob, keyOffset, valueOffset - keyOffset, StandardCharsets.UTF_8);

      final String value;
      try {
        value = decoder.decode(ByteBuffer.wrap(blob, valueOffset + 1, endOffset - valueOffset - 1)).toString();
      } catch (CharacterCodingException e) {
        return null;
      }
      if (required < REQUIRED.length && REQUIRED[required].equals(key)) {
        required++;
      }
      if (keyOffset > 0) {
        if (lastKey != null && key.compareTo(lastKey) <= 0) {
          return null;
        }
        lastKey = key;
      }
      if (result.put(key, value) != null) {
        return null;
      }
      keyOffset = endOffset + 1;
    }
    // Not found all required fields.
    if (required < REQUIRED.length) {
      return null;
    }

    return result;
  }
}
