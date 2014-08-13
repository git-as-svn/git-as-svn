package svnserver.auth;

import org.eclipse.jgit.lib.PersonIdent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User. Just user.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class User {
  @NotNull
  private final String username;
  @NotNull
  private final String realName;
  @Nullable
  private final String email;

  public User(@NotNull String username, @NotNull String realName, @Nullable String email) {
    this.username = username;
    this.realName = realName;
    this.email = email;
  }

  @NotNull
  public String getUsername() {
    return username;
  }

  @Nullable
  public PersonIdent createIdent() {
    return email == null ? null : new PersonIdent(realName, email);
  }

  @Override
  public String toString() {
    return username;
  }
}
