package svnserver.parser.token;

import org.jetbrains.annotations.NotNull;
import svnserver.StringHelper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Ключевое слово.
 * <p>
 * Бинарная строка или текст известной длины.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class StringToken implements TextToken {
  @NotNull
  private final byte[] data;

  public StringToken(@NotNull byte[] data) {
    this.data = data;
  }

  public StringToken(@NotNull String text) {
    this.data = text.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  @NotNull
  public String getText() {
    return new String(data, StandardCharsets.UTF_8);
  }

  @Override
  public void write(@NotNull OutputStream stream) throws IOException {
    write(stream, data, 0, data.length);
  }

  public static void write(@NotNull OutputStream stream, @NotNull byte[] data, int offset, int length) throws IOException {
    stream.write(Long.toString(length, 10).getBytes(StandardCharsets.ISO_8859_1));
    stream.write(':');
    stream.write(data, offset, length);
    stream.write(' ');
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final StringToken other = (StringToken) o;
    return Arrays.equals(data, other.data);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(data);
  }

  @Override
  public String toString() {
    final String value;
    if (isUtf(data)) {
      value = '\"' + new String(data, StandardCharsets.UTF_8) + '\"';
    } else {
      value = "0x" + StringHelper.toHex(data);
    }
    return "String{" + value + '}';
  }

  @SuppressWarnings("MagicNumber")
  private static boolean isUtf(byte[] data) {
    int i = 0;
    while (i < data.length) {
      int continuationBytes;
      //noinspection IfStatementWithTooManyBranches
      if (data[i] <= 0x7F)
        continuationBytes = 0;
      else if (data[i] >= 0xC0 /*11000000*/ && data[i] <= 0xDF /*11011111*/)
        continuationBytes = 1;
      else if (data[i] >= 0xE0 /*11100000*/ && data[i] <= 0xEF /*11101111*/)
        continuationBytes = 2;
      else if (data[i] >= 0xF0 /*11110000*/ && data[i] <= 0xF4 /* Cause of RFC 3629 */)
        continuationBytes = 3;
      else
        return false;
      i += 1;
      while (i < data.length && continuationBytes > 0
          && data[i] >= 0x80
          && data[i] <= 0xBF) {
        i += 1;
        continuationBytes -= 1;
      }
      if (continuationBytes != 0)
        return false;
    }
    return true;
  }
}
