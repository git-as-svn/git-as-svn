package svnserver.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerWriter;

import java.io.IOException;

/**
 * Single authentication mechanism.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public interface Authenticator {

  @NotNull
  String getMethodName();

  @Nullable
  User authenticate(@NotNull SvnServerParser parser, @NotNull SvnServerWriter writer, @NotNull String token) throws IOException, SVNException;
}
