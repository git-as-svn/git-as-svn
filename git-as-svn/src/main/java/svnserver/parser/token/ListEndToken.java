package svnserver.parser.token;

import org.jetbrains.annotations.NotNull;
import svnserver.parser.SvnServerToken;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Конец списка.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class ListEndToken implements SvnServerToken {
  @NotNull
  public static final ListEndToken instance = new ListEndToken();
  @NotNull
  private static final byte[] TOKEN = {')', ' '};

  private ListEndToken() {
  }

  @Override
  public void write(OutputStream stream) throws IOException {
    stream.write(TOKEN);
  }

  @Override
  public String toString() {
    return "ListEnd";
  }
}
