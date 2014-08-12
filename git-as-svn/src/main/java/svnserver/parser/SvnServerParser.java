package svnserver.parser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.parser.token.*;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Интерфейс для чтения токенов из потока.
 * <p>
 * http://svn.apache.org/repos/asf/subversion/trunk/subversion/libsvn_ra_svn/protocol
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnServerParser {
  private static final int DEFAULT_BUFFER_SIZE = 1024;
  // Buffer size limit for out-of-memory prevention.
  private static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024;
  @NotNull
  private final byte[] buffer;
  @NotNull
  private final InputStream stream;
  private int position;
  private int depth = 0;

  public SvnServerParser(@NotNull InputStream stream, int bufferSize) {
    this.stream = stream;
    this.buffer = new byte[Math.max(1, bufferSize)];
  }

  public SvnServerParser(@NotNull InputStream stream) {
    this(stream, DEFAULT_BUFFER_SIZE);
  }

  @NotNull
  public String readText() throws IOException {
    return readToken(TextToken.class).getText();
  }

  public int getDepth() {
    return depth;
  }

  /**
   * Чтение элемента указанного типа из потока.
   *
   * @param tokenType Тип элемента.
   * @param <T>       Тип элемента.
   * @return Прочитанный элемент.
   */
  @NotNull
  public <T extends SvnServerToken> T readToken(@NotNull Class<T> tokenType) throws IOException {
    final SvnServerToken token = readToken();
    if (!tokenType.isInstance(token)) {
      throw new IOException("Unexpected token: " + token + " (expected: " + tokenType.getName() + ')');
    }
    //noinspection unchecked
    return (T) token;
  }

  /**
   * Чтение элемента списка из потока.
   *
   * @param tokenType Тип элемента.
   * @param <T>       Тип элемента.
   * @return Прочитанный элемент.
   */
  @Nullable
  public <T extends SvnServerToken> T readItem(@NotNull Class<T> tokenType) throws IOException {
    final SvnServerToken token = readToken();
    if (ListEndToken.instance.equals(token)) {
      return null;
    }
    if (!tokenType.isInstance(token)) {
      throw new IOException("Unexpected token: " + token + " (expected: " + tokenType.getName() + ')');
    }
    //noinspection unchecked
    return (T) token;
  }

  /**
   * Чтение элемента из потока.
   *
   * @return Возвращает элемент из потока. Если элемента нет - возвращает null.
   */
  @SuppressWarnings("OverlyComplexMethod")
  @NotNull
  public SvnServerToken readToken() throws IOException {
    position = 0;
    int read;
    do {
      read = stream.read();
      // Конец потока.
      if (read < 0) {
        throw new EOFException();
      }
    } while (isSpace(read));
    if (read == '(') {
      depth++;
      return ListBeginToken.instance;
    }
    if (read == ')') {
      depth--;
      if (depth < 0) {
        throw new IOException("Unexpect end of list token.");
      }
      return ListEndToken.instance;
    }
    // Чтение чисел и строк.
    if (isDigit(read)) {
      int number = read - (int) '0';
      while (true) {
        read = stream.read();
        if (read == -1)
          throw new EOFException();
        if (!isDigit(read)) {
          break;
        }
        number = number * 10 + (read - (int) '0');
      }
      if (isSpace(read)) {
        return new NumberToken(number);
      }
      if (read == ':') {
        return readString(number);
      }
      throw new IOException("Unexpected character in stream: " + read + " (need ' ', '\\n' or ':')");
    }
    // Обычная строчка.
    if (isAlpha(read)) {
      return readWord(read);
    }
    throw new IOException("Unexpected character in stream: " + read + " (need 'a'..'z', 'A'..'Z', '0'..'9', ' ' or '\n')");
  }

  private static boolean isSpace(int data) {
    return (data == ' ')
        || (data == '\n');
  }

  private static boolean isDigit(int data) {
    return (data >= '0' && data <= '9');
  }

  @NotNull
  private StringToken readString(int length) throws IOException {
    int need = length;
    byte[] localBuffer = buffer;
    while (need > 0) {
      // Если буфер мал - увеличиваем.
      if (localBuffer.length == position) {
        localBuffer = enlargeBuffer(localBuffer);
      }
      // Читаем.
      final int readed = stream.read(localBuffer, position, Math.min(need, localBuffer.length - position));
      if (readed < 0) {
        throw new EOFException();
      }
      need -= readed;
      position += readed;
    }
    return new StringToken(Arrays.copyOf(localBuffer, length));
  }

  private static byte[] enlargeBuffer(byte[] buffer) throws IOException {
    if (buffer.length >= MAX_BUFFER_SIZE) {
      throw new IOException("Data is too long. Buffer overflow: " + buffer.length);
    }
    return Arrays.copyOf(buffer, Math.min(MAX_BUFFER_SIZE, buffer.length * 2));
  }

  private static boolean isAlpha(int data) {
    return (data >= 'a' && data <= 'z')
        || (data >= 'A' && data <= 'Z');
  }

  @NotNull
  private WordToken readWord(int first) throws IOException {
    byte[] localBuffer = buffer;
    localBuffer[position] = (byte) first;
    position++;
    while (true) {
      final int read = stream.read();
      if (read < 0) {
        throw new EOFException();
      }
      if (isSpace(read)) {
        return new WordToken(new String(localBuffer, 0, position, StandardCharsets.US_ASCII));
      }
      if (!(isAlpha(read) || isDigit(read) || (read == '-'))) {
        throw new IOException("Unexpected character in stream: " + read + " (need 'a'..'z', 'A'..'Z', '0'..'9' or '-')");
      }
      if (localBuffer.length == position) {
        localBuffer = enlargeBuffer(localBuffer);
      }
      localBuffer[position] = (byte) read;
      position++;
    }
  }

  public void skipItems() throws IOException {
    int depth = 0;
    while (depth >= 0) {
      final SvnServerToken token = readToken(SvnServerToken.class);
      if (ListBeginToken.instance.equals(token)) {
        depth++;
      }
      if (ListEndToken.instance.equals(token)) {
        depth--;
      }
    }
  }
}

