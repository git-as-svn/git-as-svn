package svnserver.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple user db with clear-text passwords.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class LocalUserDB implements UserDB, PasswordChecker {

  @NotNull
  private final Map<String, UserWithPassword> users = new HashMap<>();
  @NotNull
  private final Collection<Authenticator> authenticators = Collections.singleton(new CramMD5Authenticator(users::get));

  public LocalUserDB() {
    // TODO: read users from file
    add(new UserWithPassword(new User("bozaro", "Artem V. Navrotskiy", "bozaro@users.noreply.github.com"), "password"));
  }

  private void add(@NotNull UserWithPassword userWithPassword) {
    users.put(userWithPassword.getUser().getUserName(), userWithPassword);
  }

  @NotNull
  @Override
  public Collection<Authenticator> authenticators() {
    return authenticators;
  }

  @Nullable
  @Override
  public User check(@NotNull String username, @NotNull String password) {
    final UserWithPassword userWithPassword = users.get(username);
    if (userWithPassword == null)
      return null;

    if (!userWithPassword.getPassword().equals(password))
      return null;

    return userWithPassword.getUser();
  }
}
