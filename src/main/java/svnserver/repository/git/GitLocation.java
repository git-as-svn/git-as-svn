/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.jetbrains.annotations.NotNull;
import svnserver.context.Local;

import java.io.File;

/**
 * Git repository location.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitLocation implements Local {
  @NotNull
  private final File fullPath;

  public GitLocation(@NotNull File fullPath) {
    this.fullPath = fullPath;
  }

  @NotNull
  public File getFullPath() {
    return fullPath;
  }
}
