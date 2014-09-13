/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository;

import org.jetbrains.annotations.NotNull;

/**
 * Copy from information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class VcsCopyFrom {
  private final int revision;
  @NotNull
  private final String path;

  public VcsCopyFrom(int revision, @NotNull String path) {
    this.revision = revision;
    this.path = path;
  }

  public int getRevision() {
    return revision;
  }

  @NotNull
  public String getPath() {
    return path;
  }
}
