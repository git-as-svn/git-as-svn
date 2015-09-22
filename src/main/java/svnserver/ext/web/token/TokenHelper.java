/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.token;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import svnserver.HashHelper;
import svnserver.auth.User;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Helper for working with authentication tokens.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class TokenHelper {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(TokenHelper.class);
  @NotNull
  private static final Pattern hex = Pattern.compile("^[0-9a-fA-F]+$");

  @NotNull
  public static String createToken(@NotNull JsonWebEncryption jwe, @NotNull User user, @NotNull NumericDate expireAt) {
    try {
      JwtClaims claims = new JwtClaims();
      claims.setExpirationTime(expireAt);
      claims.setGeneratedJwtId(); // a unique identifier for the token
      claims.setIssuedAtToNow();  // when the token was issued/created (now)
      claims.setNotBeforeMinutesInThePast(0.5f); // time before which the token is not yet valid (30 seconds ago)
      if (!user.isAnonymous()) {
        claims.setSubject(user.getUserName()); // the subject/principal is whom the token is about
        setClaim(claims, "email", user.getEmail());
        setClaim(claims, "name", user.getRealName());
        setClaim(claims, "external", user.getExternalId());
      }
      jwe.setPayload(claims.toJson());
      return jwe.getCompactSerialization();
    } catch (JoseException e) {
      throw new IllegalStateException(e);
    }
  }

  @Nullable
  public static User parseToken(@NotNull JsonWebEncryption jwe, @NotNull String token) {
    try {
      jwe.setCompactSerialization(token);
      final JwtClaims claims = JwtClaims.parse(jwe.getPayload());
      final NumericDate now = NumericDate.now();
      if (claims.getExpirationTime() == null || claims.getExpirationTime().isBefore(now)) {
        return null;
      }
      if (claims.getNotBefore() == null || claims.getNotBefore().isAfter(now)) {
        return null;
      }
      if (claims.getSubject() == null) {
        return User.getAnonymous();
      }
      return User.create(
          claims.getSubject(),
          claims.getClaimValue("name", String.class),
          claims.getClaimValue("email", String.class),
          claims.getClaimValue("external", String.class)
      );
    } catch (JoseException | MalformedClaimException | InvalidJwtException e) {
      log.warn("Token parsing error: " + e.getMessage());
      return null;
    }
  }

  @NotNull
  public static byte[] secretToBytes(@NotNull String secret, int length) {
    try {
      if (secret.length() == length * 2 && hex.matcher(secret).find()) {
        return Hex.decodeHex(secret.toCharArray());
      }
      final byte[] hash = HashHelper.sha256().digest(secret.getBytes(StandardCharsets.UTF_8));
      return Arrays.copyOf(hash, length);
    } catch (DecoderException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void setClaim(JwtClaims claims, @NotNull String name, @Nullable Object value) {
    if (value != null) {
      claims.setClaim(name, value);
    }
  }
}
