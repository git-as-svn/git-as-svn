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
import java.util.Objects;

/**
 * User. Just user.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class User {
  @NotNull
  private static final User anonymousUser = new User("anonymous", "anonymous", null, null, true);

  private final boolean isAnonymous;
  @NotNull
  private final String userName;
  @NotNull
  private final String realName;
  @Nullable
  private final String email;
  @Nullable
  private final String externalId;

  public static User create(@NotNull String userName, @NotNull String realName, @Nullable String email, @Nullable String externalId) {
    return new User(userName, realName, email, externalId, false);
  }

  protected User(@NotNull String userName, @NotNull String realName, @Nullable String email, @Nullable String externalId, boolean isAnonymous) {
    this.userName = userName;
    this.realName = realName;
    this.email = email;
    this.externalId = externalId;
    this.isAnonymous = isAnonymous;
  }

  @Nullable
  public String getExternalId() {
    return externalId;
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
    return isAnonymous;
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

    return Objects.equals(externalId, user.externalId)
        && Objects.equals(email, user.email)
        && userName.equals(user.userName)
        && realName.equals(user.realName)
        && (isAnonymous == user.isAnonymous);
  }

  @Override
  public int hashCode() {
    int result = userName.hashCode();
    result = 31 * result + realName.hashCode();
    result = 31 * result + (email != null ? email.hashCode() : 0);
    return result;
  }

  public static User getAnonymous() {
    return anonymousUser;
  }
}
