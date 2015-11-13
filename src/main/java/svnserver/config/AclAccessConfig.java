/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.jetbrains.annotations.NotNull;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class AclAccessConfig {

  @NotNull
  private String path = "/";

  @NotNull
  private String[] allowed = {};

  public AclAccessConfig() {
  }

  public AclAccessConfig(@NotNull String path, @NotNull String[] allowed) {
    this.path = path;
    this.allowed = allowed;
  }

  @NotNull
  public String getPath() {
    return path;
  }

  public void setPath(@NotNull String path) {
    this.path = path.trim();
  }

  @NotNull
  public String[] getAllowed() {
    return allowed;
  }

  public void setAllowed(@NotNull String[] allowed) {
    this.allowed = allowed;
  }
}
