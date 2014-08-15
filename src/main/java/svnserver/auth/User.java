package svnserver.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User. Just user.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class User {
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

  @Override
  public String toString() {
    return userName;
  }
}
