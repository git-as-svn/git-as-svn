/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.jetbrains.annotations.NotNull;
import svnserver.context.Local;

import java.nio.file.Path;

/**
 * Git repository location.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitLocation implements Local {
  @NotNull
  private final Path fullPath;

  public GitLocation(@NotNull Path fullPath) {
    this.fullPath = fullPath;
  }

  @NotNull
  public Path getFullPath() {
    return fullPath;
  }
}
