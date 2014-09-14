/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.parser.token;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Ключевое слово.
 * <p>
 * Допустимые символы: 'a'..'z', 'A'..'Z', '-', '0'..'9'
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class WordToken implements TextToken {
  @NotNull
  private final String word;

  public WordToken(@NotNull String word) {
    this.word = word;
  }

  @Override
  @NotNull
  public String getText() {
    return word;
  }

  @Override
  public void write(@NotNull OutputStream stream) throws IOException {
    write(stream, word);
  }

  public static void write(@NotNull OutputStream stream, @NotNull String word) throws IOException {
    stream.write(word.getBytes(StandardCharsets.US_ASCII));
    stream.write(' ');
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final WordToken other = (WordToken) o;
    return word.equals(other.word);
  }

  @Override
  public int hashCode() {
    return word.hashCode();
  }

  @Override
  public String toString() {
    return "Word{" + word + '}';
  }
}
