package svnserver.parser;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Интерфейс для токенов.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface SvnServerToken {
  /**
   * Запись token-а в поток.
   *
   * @param stream Поток.
   * @throws IOException
   */
  void write(OutputStream stream) throws IOException;
}
