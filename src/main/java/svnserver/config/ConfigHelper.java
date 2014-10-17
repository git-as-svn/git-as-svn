/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Useful for work with config.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ConfigHelper {
  @NotNull
  public static File joinPath(@NotNull File basePath, @NotNull String localPath) {
    final File path = new File(localPath);
    return path.isAbsolute() ? path : new File(basePath, localPath);
  }
}
