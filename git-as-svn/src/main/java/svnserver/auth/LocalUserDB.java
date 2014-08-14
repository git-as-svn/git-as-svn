package svnserver.auth;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple user db with clear-text passwords.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class LocalUserDB implements UserDB {

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
}
