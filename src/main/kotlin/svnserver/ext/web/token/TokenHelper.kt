/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.token

import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.jose4j.jwe.JsonWebEncryption
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.MalformedClaimException
import org.jose4j.jwt.NumericDate
import org.jose4j.jwt.consumer.InvalidJwtException
import org.jose4j.lang.JoseException
import svnserver.HashHelper
import svnserver.Loggers
import svnserver.UserType
import svnserver.auth.User
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.regex.Pattern

/**
 * Helper for working with authentication tokens.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
object TokenHelper {
    private val log = Loggers.web
    private val hex = Pattern.compile("^[0-9a-fA-F]+$")
    fun createToken(jwe: JsonWebEncryption, user: User, expireAt: NumericDate): String {
        return try {
            val claims = JwtClaims()
            claims.expirationTime = expireAt
            claims.setGeneratedJwtId() // a unique identifier for the token
            claims.setIssuedAtToNow() // when the token was issued/created (now)
            claims.setNotBeforeMinutesInThePast(0.5f) // time before which the token is not yet valid (30 seconds ago)
            if (!user.isAnonymous) {
                claims.subject = user.username // the subject/principal is whom the token is about
                setClaim(claims, "email", user.email)
                setClaim(claims, "name", user.realName)
                setClaim(claims, "external", user.externalId)
                setClaim(claims, "type", user.type.name)
            }
            jwe.payload = claims.toJson()
            jwe.compactSerialization
        } catch (e: JoseException) {
            throw IllegalStateException(e)
        }
    }

    private fun setClaim(claims: JwtClaims, name: String, value: Any?) {
        if (value != null) {
            claims.setClaim(name, value)
        }
    }

    fun parseToken(jwe: JsonWebEncryption, token: String, tokenEnsureTime: Int): User? {
        return try {
            jwe.compactSerialization = token
            val claims = JwtClaims.parse(jwe.payload)
            val now = NumericDate.now()
            val expire = NumericDate.fromMilliseconds(now.valueInMillis)
            if (tokenEnsureTime > 0) {
                expire.addSeconds(tokenEnsureTime.toLong())
            }
            if (claims.expirationTime == null || claims.expirationTime.isBefore(expire)) {
                return null
            }
            if (claims.notBefore == null || claims.notBefore.isAfter(now)) {
                return null
            }
            if (claims.subject == null) {
                User.anonymous
            } else User.create(
                claims.subject,
                claims.getClaimValue("name", String::class.java),
                claims.getClaimValue("email", String::class.java),
                claims.getClaimValue("external", String::class.java),
                UserType.valueOf(claims.getClaimValue("type", String::class.java)),
                null
            )
        } catch (e: JoseException) {
            log.warn("Token parsing error: " + e.message)
            null
        } catch (e: MalformedClaimException) {
            log.warn("Token parsing error: " + e.message)
            null
        } catch (e: InvalidJwtException) {
            log.warn("Token parsing error: " + e.message)
            null
        }
    }

    fun secretToBytes(secret: String, length: Int): ByteArray {
        return try {
            if (secret.length == length * 2 && hex.matcher(secret).find()) {
                return Hex.decodeHex(secret.toCharArray())
            }
            val hash = HashHelper.sha256().digest(secret.toByteArray(StandardCharsets.UTF_8))
            Arrays.copyOf(hash, length)
        } catch (e: DecoderException) {
            throw IllegalStateException(e)
        }
    }
}
