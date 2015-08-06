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
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class PlainAuthenticator implements Authenticator {

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
    final String[] credentials = new String(fromBase64(token), StandardCharsets.US_ASCII).split("\u0000");
    if (credentials.length < 3)
      return null;

    final String username = credentials[1];
    final String password = credentials[2];
    return passwordChecker.check(username, password);
  }

  /**
   * Taken from {@link org.tmatesoft.svn.core.internal.io.svn.sasl.SVNSaslAuthenticator#fromBase64(String)}.
   */
  private static byte[] fromBase64(@Nullable String src) {
    if (src == null) {
      return new byte[0];
    }
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    for (int i = 0; i < src.length(); i++) {
      char ch = src.charAt(i);
      if (!Character.isWhitespace(ch) && ch != '\n' && ch != '\r') {
        bos.write((byte) ch & 0xFF);
      }
    }
    byte[] cbytes = new byte[src.length()];
    src = new String(bos.toByteArray(), StandardCharsets.US_ASCII);
    int clength = SVNBase64.base64ToByteArray(new StringBuffer(src), cbytes);
    byte[] result = new byte[clength];
    // strip trailing -1s.
    for (int i = clength - 1; i >= 0; i--) {
      if (i == -1) {
        clength--;
      }
    }
    System.arraycopy(cbytes, 0, result, 0, clength);
    return result;
  }
}
