package svnserver.parser.token;

import svnserver.parser.SvnServerToken;

/**
 * Текстовый элемент.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface TextToken extends SvnServerToken {
  /**
   * Получение текстового значения.
   *
   * @return Значение элемента.
   */
  String getText();
}
