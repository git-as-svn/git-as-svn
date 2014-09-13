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
public final class GroupConfig {

  @NotNull
  public static final GroupConfig[] emptyArray = {};

  @NotNull
  private String name;

  @NotNull
  private String[] users = {};

  @NotNull
  public String getName() {
    return name;
  }

  public void setName(@NotNull String name) {
    this.name = name.trim();
  }

  @NotNull
  public String[] getUsers() {
    return users;
  }

  public void setUsers(@NotNull String[] users) {
    this.users = users;
  }
}
