package svnserver.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerWriter;

import java.io.IOException;

/**
 * Anonymous authentication.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
final class AnonymousAuthenticator implements Authenticator {

  @NotNull
  private static final User anonymous = new User("ANONYMOUS", "ANONYMOUS", null);

  @NotNull
  @Override
  public String getMethodName() {
    return "ANONYMOUS";
  }

  @Nullable
  @Override
  public User authenticate(@NotNull SvnServerParser parser, @NotNull SvnServerWriter writer, @NotNull String token) throws IOException {
    return anonymous;
  }
}
