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
      return path;
    }
    if (localPath.startsWith("/")) {
      return localPath;
    }
    return path + (path.endsWith("/") ? "" : "/") + localPath;
  }
}
