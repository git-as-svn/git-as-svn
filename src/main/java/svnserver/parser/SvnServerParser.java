/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
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
  private static final int DEFAULT_BUFFER_SIZE = 32 * 1024;
  // Buffer size limit for out-of-memory prevention.
  private static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024;
  @NotNull
  private final InputStream stream;
  private int depth = 0;

  @NotNull
  private final byte[] buffer;
  private int offset = 0;
  private int limit = 0;

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

  public int readNumber() throws IOException {
    return readToken(NumberToken.class).getNumber();
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
    byte read = skipSpaces();
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
      return readNumberToken(read);
    }
    // Обычная строчка.
    if (isAlpha(read)) {
      return readWord();
    }
    throw new IOException("Unexpected character in stream: " + read + " (need 'a'..'z', 'A'..'Z', '0'..'9', ' ' or '\n')");
  }

  private SvnServerToken readNumberToken(byte first) throws IOException {
    int result = first - '0';
    while (true) {
      while (offset < limit) {
        final byte data = buffer[offset];
        offset++;
        if ((data < '0') || (data > '9')) {
          if (data == ':') {
            return readString(result);
          }
          if (isSpace(data)) {
            return new NumberToken(result);
          }
          throw new IOException("Unexpected character in stream: " + data + " (need ' ', '\\n' or ':')");
        }
        result = result * 10 + (data - '0');
      }
      if (limit < 0) {
        throw new EOFException();
      }
      offset = 0;
      limit = stream.read(buffer);
    }
  }

  private byte skipSpaces() throws IOException {
    while (true) {
      while (offset < limit) {
        final byte data = buffer[offset];
        offset++;
        if (!isSpace(data)) {
          return data;
        }
      }
      if (limit < 0) {
        throw new EOFException();
      }
      offset = 0;
      limit = stream.read(buffer);
    }
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
    if (length >= MAX_BUFFER_SIZE) {
      throw new IOException("Data is too long. Buffer overflow: " + buffer.length);
    }
    if (limit < 0) {
      throw new EOFException();
    }
    final byte[] token = new byte[length];
    if (length <= limit - offset) {
      System.arraycopy(buffer, offset, token, 0, length);
      offset += length;
    } else {
      int position = limit - offset;
      System.arraycopy(buffer, offset, token, 0, position);
      limit = 0;
      offset = 0;
      while (position < length) {
        int size = stream.read(token, position, length - position);
        if (size < 0) {
          limit = -1;
          throw new EOFException();
        }
        position += size;
      }
    }
    return new StringToken(Arrays.copyOf(token, length));
  }

  private static boolean isAlpha(int data) {
    return (data >= 'a' && data <= 'z')
        || (data >= 'A' && data <= 'Z');
  }

  @NotNull
  private WordToken readWord() throws IOException {
    int begin = offset - 1;
    while (offset < limit) {
      final byte data = buffer[offset];
      offset++;
      if (isSpace(data)) {
        return new WordToken(new String(buffer, begin, offset - begin - 1, StandardCharsets.US_ASCII));
      }
      if (!(isAlpha(data) || isDigit(data) || (data == '-'))) {
        throw new IOException("Unexpected character in stream: " + data + " (need 'a'..'z', 'A'..'Z', '0'..'9' or '-')");
      }
    }
    System.arraycopy(buffer, begin, buffer, 0, limit - begin);
    limit = offset - begin;
    offset = limit;
    while (limit < buffer.length) {
      int size = stream.read(buffer, limit, buffer.length - limit);
      if (size < 0) {
        throw new EOFException();
      }
      limit += size;
      while (offset < limit) {
        final byte data = buffer[offset];
        offset++;
        if (isSpace(data)) {
          return new WordToken(new String(buffer, 0, offset - 1, StandardCharsets.US_ASCII));
        }
        if (!(isAlpha(data) || isDigit(data) || (data == '-'))) {
          throw new IOException("Unexpected character in stream: " + data + " (need 'a'..'z', 'A'..'Z', '0'..'9' or '-')");
        }
      }
    }
    throw new IOException("Data is too long. Buffer overflow: " + buffer.length);
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

