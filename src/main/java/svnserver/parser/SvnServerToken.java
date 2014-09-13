/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
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
