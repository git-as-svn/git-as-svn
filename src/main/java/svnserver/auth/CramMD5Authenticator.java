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
import svnserver.StringHelper;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerWriter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.UUID;
import java.util.function.Function;

/**
 * Performs CRAM-MD5 authentication.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
final class CramMD5Authenticator implements Authenticator {

  CramMD5Authenticator(@NotNull Function<String, UserWithPassword> lookup) {
    this.lookup = lookup;
  }

  @NotNull
  private final Function<String, UserWithPassword> lookup;

  @NotNull
  @Override
  public String getMethodName() {
    return "CRAM-MD5";
  }

  @Nullable
  @Override
  public User authenticate(@NotNull SvnServerParser parser, @NotNull SvnServerWriter writer, @NotNull String token) throws IOException {
    // Выполняем авторизацию.
    String msgId = UUID.randomUUID().toString();
    writer
        .listBegin()
        .word("step")
        .listBegin()
        .string(msgId)
        .listEnd()
        .listEnd();

    // Читаем логин и пароль.
    final String[] authData = parser.readText().split(" ", 2);
    final String username = authData[0];

    final UserWithPassword userWithPassword = lookup.apply(username);
    if (userWithPassword == null)
      return null;

    final String authRequire = hmac(msgId, userWithPassword.getPassword());
    if (!authData[1].equals(authRequire))
      return null;

    return userWithPassword.getUser();
  }

  private static String hmac(@NotNull String sessionKey, @NotNull String password) {
    try {
      final SecretKeySpec keySpec = new SecretKeySpec(password.getBytes(), "HmacMD5");
      final Mac mac = Mac.getInstance("HmacMD5");
      mac.init(keySpec);
      return StringHelper.toHex(mac.doFinal(sessionKey.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
