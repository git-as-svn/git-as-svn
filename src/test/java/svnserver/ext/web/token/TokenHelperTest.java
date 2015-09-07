/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.token;

import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwt.NumericDate;
import org.testng.Assert;
import org.testng.annotations.Test;
import svnserver.auth.User;

/**
 * Tests for TokenHelper.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class TokenHelperTest {
  @Test
  public void simpleWithoutExternal() {
    final User expected = User.create("foo", "bar", "foo@example.com", null);
    final String token = TokenHelper.createToken(createToken("secret"), expected, NumericDate.fromMilliseconds(System.currentTimeMillis() + 2000));
    final User actual = TokenHelper.parseToken(createToken("secret"), token);
    Assert.assertNotNull(actual);
    Assert.assertEquals(actual, expected);
  }

  @Test
  public void simpleWithExternal() {
    final User expected = User.create("foo", "bar", "foo@example.com", "user-1");
    final String token = TokenHelper.createToken(createToken("secret"), expected, NumericDate.fromMilliseconds(System.currentTimeMillis() + 2000));
    final User actual = TokenHelper.parseToken(createToken("secret"), token);
    Assert.assertNotNull(actual);
    Assert.assertEquals(actual, expected);
  }

  @Test
  public void anonymous() {
    final User expected = User.getAnonymous();
    final String token = TokenHelper.createToken(createToken("secret"), expected, NumericDate.fromMilliseconds(System.currentTimeMillis() + 2000));
    final User actual = TokenHelper.parseToken(createToken("secret"), token);
    Assert.assertNotNull(actual);
    Assert.assertEquals(actual, expected);
  }

  @Test
  public void invalidToken() {
    final User expected = User.create("foo", "bar", "foo@example.com", null);
    final String token = TokenHelper.createToken(createToken("big secret"), expected, NumericDate.fromMilliseconds(System.currentTimeMillis() + 2000));
    final User actual = TokenHelper.parseToken(createToken("small secret"), token);
    Assert.assertNull(actual);
  }

  @Test
  public void expiredToken() {
    final User expected = User.create("foo", "bar", "foo@example.com", null);
    final String token = TokenHelper.createToken(createToken("secret"), expected, NumericDate.fromMilliseconds(System.currentTimeMillis() - 2000));
    final User actual = TokenHelper.parseToken(createToken("secret"), token);
    Assert.assertNull(actual);
  }

  @Test
  public void secretToBytesHash() {
    final byte[] bytes = TokenHelper.secretToBytes("foo", 0x10);
    Assert.assertEquals(0x10, bytes.length);
  }

  @Test
  public void secretToBytesHex() {
    final byte[] expected = {0x12, (byte) 0xAF, 0x34, (byte) 0x8E};
    final byte[] bytesHex = TokenHelper.secretToBytes(Hex.encodeHexString(expected), 4);
    Assert.assertEquals(bytesHex, expected);

    final byte[] bytesHash = TokenHelper.secretToBytes(Hex.encodeHexString(expected), 5);
    Assert.assertEquals(bytesHash.length, 5);
  }

  @NotNull
  private JsonWebEncryption createToken(@NotNull String secret) {
    return new EncryptionFactoryAes(secret).create();
  }
}
