/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth;

import org.jetbrains.annotations.NotNull;

/**
 * User with clear-text password. Extremely insecure.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class UserWithPassword {

  @NotNull
  private final User user;
  @NotNull
  private final String password;

  public UserWithPassword(@NotNull User user, @NotNull String password) {
    this.user = user;
    this.password = password;
  }

  @NotNull
  public User getUser() {
    return user;
  }

  @NotNull
  public String getPassword() {
    return password;
  }
}
