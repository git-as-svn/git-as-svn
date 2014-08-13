package svnserver.auth;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * User storage.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public interface UserDB {

  @NotNull
  public Collection<Authenticator> authenticators();
}
