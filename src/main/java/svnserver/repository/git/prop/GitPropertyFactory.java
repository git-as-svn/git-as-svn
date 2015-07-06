/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop;

import org.atteo.classindex.IndexSubclasses;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Factory for creating GitProperty.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@IndexSubclasses
public interface GitPropertyFactory {

  /**
   * Return mappeg git file name.
   *
   * @return File name.
   */
  @NotNull
  String getFileName();

  /**
   * Create git property worker by file content.
   *
   * @param content File content.
   * @return Git property worker.
   */
  @NotNull
  GitProperty[] create(@NotNull String content) throws IOException;
}
