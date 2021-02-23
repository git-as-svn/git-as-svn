/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.token

import org.apache.commons.codec.binary.Hex
import org.jose4j.jwe.JsonWebEncryption
import org.jose4j.jwt.NumericDate
import org.testng.*
import org.testng.annotations.*
import svnserver.UserType
import svnserver.auth.*
import svnserver.ext.web.token.TokenHelper.createToken
import svnserver.ext.web.token.TokenHelper.parseToken
import svnserver.ext.web.token.TokenHelper.secretToBytes

/**
 * Tests for TokenHelper.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class TokenHelperTest {
    @Test
    fun simpleWithoutExternal() {
        val expected = User.create("foo", "bar", "foo@example.com", null, UserType.Local, null)
        val token = createToken(createToken("secret"), expected, NumericDate.fromMilliseconds(System.currentTimeMillis() + 2000))
        val actual = parseToken(createToken("secret"), token, 0)
        Assert.assertEquals(actual, expected)
    }

    private fun createToken(secret: String): JsonWebEncryption {
        return EncryptionFactoryAes(secret).create()
    }

    @Test
    fun simpleWithExternal() {
        val expected = User.create("foo", "bar", "foo@example.com", "user-1", UserType.Local, null)
        val token = createToken(createToken("secret"), expected, NumericDate.fromMilliseconds(System.currentTimeMillis() + 2000))
        val actual = parseToken(createToken("secret"), token, 0)
        Assert.assertEquals(actual, expected)
    }

    @Test
    fun anonymous() {
        val expected: User = User.anonymous
        val token = createToken(createToken("secret"), expected, NumericDate.fromMilliseconds(System.currentTimeMillis() + 2000))
        val actual = parseToken(createToken("secret"), token, 0)
        Assert.assertEquals(actual, expected)
    }

    @Test
    fun invalidToken() {
        val expected = User.create("foo", "bar", "foo@example.com", null, UserType.Local, null)
        val token = createToken(createToken("big secret"), expected, NumericDate.fromMilliseconds(System.currentTimeMillis() + 2000))
        val actual = parseToken(createToken("small secret"), token, 0)
        Assert.assertNull(actual)
    }

    @Test
    fun expiredToken() {
        val expected = User.create("foo", "bar", "foo@example.com", null, UserType.Local, null)
        val token = createToken(createToken("secret"), expected, NumericDate.fromMilliseconds(System.currentTimeMillis() - 2000))
        val actual = parseToken(createToken("secret"), token, 0)
        Assert.assertNull(actual)
    }

    @Test
    fun secretToBytesHash() {
        val bytes = secretToBytes("foo", 0x10)
        Assert.assertEquals(0x10, bytes.size)
    }

    @Test
    fun secretToBytesHex() {
        val expected = byteArrayOf(0x12, 0xAF.toByte(), 0x34, 0x8E.toByte())
        val bytesHex = secretToBytes(Hex.encodeHexString(expected), 4)
        Assert.assertEquals(bytesHex, expected)
        val bytesHash = secretToBytes(Hex.encodeHexString(expected), 5)
        Assert.assertEquals(bytesHash.size, 5)
    }
}
