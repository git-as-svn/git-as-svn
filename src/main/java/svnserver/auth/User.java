/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.UserType;

import java.util.Map;
import java.util.Objects;

/**
 * User. Just user.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class User {
  @NotNull
  private static final User anonymousUser = new User("$anonymous", "anonymous", null, null, true, UserType.Local, null);

  private final boolean isAnonymous;
  @NotNull
  private final String username;
  @NotNull
  private final String realName;
  @Nullable
  private final String email;
  @Nullable
  private final String externalId;
  @NotNull
  private final UserType type;
  @Nullable
  private final LfsCredentials lfsCredentials;

  protected User(@NotNull String username, @NotNull String realName, @Nullable String email, @Nullable String externalId, boolean isAnonymous, @NotNull UserType type, @Nullable LfsCredentials lfsCredentials) {
    this.username = username;
    this.realName = realName;
    this.email = email;
    this.externalId = externalId;
    this.isAnonymous = isAnonymous;
    this.type = type;
    this.lfsCredentials = lfsCredentials;
  }

  @NotNull
  public static User create(@NotNull String username, @NotNull String realName, @Nullable String email, @Nullable String externalId, @NotNull UserType type, @Nullable LfsCredentials lfsCredentials) {
    return new User(username, realName, email, externalId, false, type, lfsCredentials);
  }

  public static User getAnonymous() {
    return anonymousUser;
  }

  @Nullable
  public LfsCredentials getLfsCredentials() {
    return lfsCredentials;
  }

  @Nullable
  public String getExternalId() {
    return externalId;
  }

  public boolean isAnonymous() {
    return isAnonymous;
  }

  @NotNull
  public UserType getType() {
    return type;
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
    env.put("GAS_LOGIN", getUsername());
  }

  @Nullable
  public String getEmail() {
    return email;
  }

  @NotNull
  public String getRealName() {
    return realName;
  }

  @NotNull
  public String getUsername() {
    return username;
  }

  @Override
  public int hashCode() {
    int result = username.hashCode();
    result = 31 * result + realName.hashCode();
    result = 31 * result + type.hashCode();
    result = 31 * result + (email != null ? email.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    User user = (User) o;

    return Objects.equals(externalId, user.externalId)
        && Objects.equals(email, user.email)
        && username.equals(user.username)
        && realName.equals(user.realName)
        && type.equals(user.type)
        && (isAnonymous == user.isAnonymous);
  }

  @Override
  public String toString() {
    return username;
  }

  public static class LfsCredentials {
    @NotNull
    public final String username;
    @NotNull
    public final String password;

    public LfsCredentials(@NotNull String username, @NotNull String password) {
      this.username = username;
      this.password = password;
    }
  }
}
