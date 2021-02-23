/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth

import svnserver.StringHelper
import svnserver.server.SessionContext
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.util.*
import java.util.function.Function
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Performs CRAM-MD5 authentication.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
internal class CramMD5Authenticator constructor(private val lookup: Function<String, UserWithPassword?>) : Authenticator {
    override val methodName: String
        get() {
            return "CRAM-MD5"
        }

    @Throws(IOException::class)
    override fun authenticate(context: SessionContext, token: String): User? {
        // Выполняем авторизацию.
        val msgId: String = UUID.randomUUID().toString()
        context.writer
            .listBegin()
            .word("step")
            .listBegin()
            .string(msgId)
            .listEnd()
            .listEnd()

        // Читаем логин и пароль.
        val authData: Array<String> = context.parser.readText().split(" ", ignoreCase = false, limit = 2).toTypedArray()
        val username: String = authData[0]
        val userWithPassword: UserWithPassword = lookup.apply(username) ?: return null
        val authRequire: String = hmac(msgId, userWithPassword.password)
        if (authData[1] != authRequire) return null
        return userWithPassword.user
    }

    companion object {
        private fun hmac(sessionKey: String, password: String): String {
            try {
                val keySpec = SecretKeySpec(password.toByteArray(), "HmacMD5")
                val mac: Mac = Mac.getInstance("HmacMD5")
                mac.init(keySpec)
                return StringHelper.toHex(mac.doFinal(sessionKey.toByteArray(StandardCharsets.UTF_8)))
            } catch (e: GeneralSecurityException) {
                throw IllegalStateException(e)
            }
        }
    }
}
