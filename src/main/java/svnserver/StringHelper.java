package svnserver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Useful string utilites.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class StringHelper {
  @NotNull
  private static final char[] DIGITS = "0123456789abcdef".toCharArray();
  @NotNull
  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  @SuppressWarnings("MagicNumber")
  public static String toHex(byte[] data) {
    final StringBuilder result = new StringBuilder();
    for (byte i : data) {
      result.append(DIGITS[(i >> 4) & 0x0F]);
      result.append(DIGITS[i & 0x0F]);
    }
    return result.toString();
  }

  @NotNull
  public static String formatDate(long time) {
    final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    df.setTimeZone(UTC);
    return df.format(new Date(time));
  }

  @NotNull
  public static String joinPath(@NotNull String path, @Nullable String localPath) {
    if (localPath == null || localPath.isEmpty()) {
      return normalize(path);
    }
    if (localPath.startsWith("/")) {
      return normalize(localPath);
    }
    return normalize(path + (path.endsWith("/") ? "" : "/") + localPath);
  }

  @NotNull
  private static String normalize(@NotNull String path) {
    if (path.isEmpty()) return "";
    String result = path;
    if (!result.startsWith("/")) {
      result = "/" + result;
    }
    return result.endsWith("/") ? result.substring(0, result.length() - 1) : result;
  }

  @NotNull
  public static String parentDir(@NotNull String fullPath) {
    int index = fullPath.lastIndexOf('/');
    return index >= 0 ? fullPath.substring(0, index) : "";
  }

  @NotNull
  public static String baseName(@NotNull String fullPath) {
    return fullPath.substring(fullPath.lastIndexOf('/') + 1);
  }

  /**
   * Returns true, if parentPath is base path of childPath.
   *
   * @param parentPath Parent path.
   * @param childPath  Child path.
   * @return Returns true, if parentPath is base path of childPath.
   */
  public static boolean isParentPath(@NotNull String parentPath, @NotNull String childPath) {
    return childPath.equals(parentPath)
        || childPath.startsWith(parentPath) && (childPath.charAt(parentPath.length()) == '/');
  }
}
