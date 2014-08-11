package svnserver.parser;

import org.jetbrains.annotations.NotNull;
import svnserver.parser.token.*;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Интерфейс для записи данных в поток.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnServerWriter {
  @NotNull
  private final OutputStream stream;
  private int depth = 0;

  public SvnServerWriter(@NotNull OutputStream stream) {
    this.stream = new BufferedOutputStream(stream);
  }

  @NotNull
  public SvnServerWriter listBegin() throws IOException {
    return write(ListBeginToken.instance);
  }

  @NotNull
  public SvnServerWriter listEnd() throws IOException {
    return write(ListEndToken.instance);
  }

  @NotNull
  public SvnServerWriter word(@NotNull String word) throws IOException {
    WordToken.write(stream, word);
    if (depth == 0) stream.flush();
    return this;
  }

  @SuppressWarnings("QuestionableName")
  @NotNull
  public SvnServerWriter string(@NotNull String text) throws IOException {
    return binary(text.getBytes(StandardCharsets.UTF_8));
  }

  @NotNull
  public SvnServerWriter binary(@NotNull byte[] data) throws IOException {
    StringToken.write(stream, data);
    if (depth == 0) stream.flush();
    return this;
  }

  @NotNull
  public SvnServerWriter number(long number) throws IOException {
    NumberToken.write(stream, number);
    if (depth == 0) stream.flush();
    return this;
  }

  @NotNull
  public SvnServerWriter write(@NotNull SvnServerToken token) throws IOException {
    token.write(stream);
    if (token.equals(ListBeginToken.instance)) {
      depth++;
    } else if (token.equals(ListEndToken.instance)) {
      depth--;
      if (depth < 0) {
        throw new IllegalStateException("Too many closed lists.");
      }
    }
    if (depth == 0) stream.flush();
    return this;
  }
}
