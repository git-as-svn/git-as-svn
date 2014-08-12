package svnserver;

import org.jetbrains.annotations.NotNull;

/**
 * Useful string utilites.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class StringHelper {
  @NotNull
  private static final char[] DIGITS = "0123456789abcdef".toCharArray();

  @SuppressWarnings("MagicNumber")
  public static String toHex(byte[] data) {
    final StringBuilder result = new StringBuilder();
    for (byte i : data) {
      result.append(DIGITS[(i >> 4) & 0x0F]);
      result.append(DIGITS[i & 0x0F]);
    }
    return result.toString();
  }
}
