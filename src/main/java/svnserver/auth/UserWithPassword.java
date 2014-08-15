package svnserver.auth;

import org.jetbrains.annotations.NotNull;

/**
 * User with clear-text password. Extremely insecure.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
final class UserWithPassword {

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
