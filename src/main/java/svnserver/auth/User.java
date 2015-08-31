/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * User. Just user.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public class User {
  @NotNull
  private final String userName;
  @NotNull
  private final String realName;
  @Nullable
  private final String email;

  public User(@NotNull String userName, @NotNull String realName, @Nullable String email) {
    this.userName = userName;
    this.realName = realName;
    this.email = email;
  }

  @NotNull
  public String getUserName() {
    return userName;
  }

  @NotNull
  public String getRealName() {
    return realName;
  }

  @Nullable
  public String getEmail() {
    return email;
  }

  public boolean isAnonymous() {
    return email == null;
  }

  /**
   * Set user information to environment variables
   *
   * @param env Environment variables
   */
  public void updateEnvironment(@NotNull Map<String, String> env) {
    if (getEmail() != null) {
      env.put("GAS_EMAIL", getEmail());
    }
    env.put("GAS_NAME", getRealName());
    env.put("GAS_LOGIN", getUserName());
  }

  @Override
  public String toString() {
    return userName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    User user = (User) o;

    if (!userName.equals(user.userName)) return false;
    if (!realName.equals(user.realName)) return false;
    return !(email != null ? !email.equals(user.email) : user.email != null);
  }

  @Override
  public int hashCode() {
    int result = userName.hashCode();
    result = 31 * result + realName.hashCode();
    result = 31 * result + (email != null ? email.hashCode() : 0);
    return result;
  }
}
