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
import org.tmatesoft.svn.core.SVNException;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class PlainAuthenticator implements Authenticator, PasswordChecker {

  @NotNull
  private final PasswordChecker passwordChecker;

  public PlainAuthenticator(@NotNull PasswordChecker passwordChecker) {
    this.passwordChecker = passwordChecker;
  }

  @NotNull
  @Override
  public String getMethodName() {
    return "PLAIN";
  }

  @Nullable
  @Override
  public User authenticate(@NotNull SvnServerParser parser, @NotNull SvnServerWriter writer, @NotNull String token) throws IOException, SVNException {
    final String decodedToken = new String(Base64.getDecoder().decode(token.trim()), StandardCharsets.US_ASCII);
    final String[] credentials = decodedToken.split("\u0000");
    if (credentials.length < 3)
      return null;

    final String username = credentials[1];
    final String password = credentials[2];
    return check(username, password);
  }

  @Nullable
  @Override
  public User check(@NotNull String userName, @NotNull String password) throws SVNException, IOException {
    return passwordChecker.check(userName, password);
  }
}
