/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs

import jakarta.servlet.http.HttpServletResponse
import org.jose4j.jwt.NumericDate
import org.tmatesoft.svn.core.SVNException
import ru.bozaro.gitlfs.common.Constants
import ru.bozaro.gitlfs.common.data.Link
import ru.bozaro.gitlfs.server.ServerError
import svnserver.auth.User
import svnserver.auth.UserDB
import svnserver.context.SharedContext
import svnserver.ext.web.server.WebServer
import svnserver.ext.web.token.TokenHelper
import java.net.URI
import java.util.*
import kotlin.math.ceil

/**
 * Helper for git-lfs-authenticate implementation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
object LfsAuthHelper {
    @Throws(ServerError::class)
    fun createToken(
        context: SharedContext,
        baseLfsUrl: URI,
        userId: String?,
        authMode: AuthMode,
        tokenExpireSec: Long,
        tokenEnsureTime: Float
    ): Link {
        return try {
            val userDB = context.sure(UserDB::class.java)
            val user: User? = if (authMode == AuthMode.Anonymous) {
                User.anonymous
            } else if (userId == null) {
                throw ServerError(HttpServletResponse.SC_BAD_REQUEST, "Parameter 'userId' not specified")
            } else {
                if (authMode == AuthMode.Username) {
                    userDB.lookupByUserName(userId)
                } else {
                    userDB.lookupByExternal(userId)
                }
            }
            if (user == null) {
                throw ServerError(HttpServletResponse.SC_NOT_FOUND, "User not found")
            }
            createToken(context, baseLfsUrl, user, tokenExpireSec, tokenEnsureTime)
        } catch (e: SVNException) {
            throw ServerError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Can't get user information. See server log for more details", e)
        }
    }

    private fun createToken(
        context: SharedContext,
        baseLfsUrl: URI,
        user: User,
        tokenExpireSec: Long,
        tokenEnsureTime: Float
    ): Link {
        val expireSec = if (tokenExpireSec <= 0) LocalLfsConfig.DEFAULT_TOKEN_EXPIRE_SEC else tokenExpireSec
        val ensureSec = ceil((expireSec * tokenEnsureTime).toDouble()).toLong()
        val now = NumericDate.now()
        val expireAt = NumericDate.fromSeconds(now.value + expireSec)
        val ensureAt = NumericDate.fromSeconds(now.value + ensureSec)
        return Link(
            baseLfsUrl,
            createTokenHeader(context, user, expireAt),
            Date(ensureAt.valueInMillis)
        )
    }

    fun createTokenHeader(
        context: SharedContext,
        user: User,
        expireAt: NumericDate
    ): Map<String, String> {
        val webServer = context.sure(WebServer::class.java)
        val accessToken = TokenHelper.createToken(webServer.createEncryption(), user, expireAt)
        return mapOf(Constants.HEADER_AUTHORIZATION to WebServer.AUTH_TOKEN + accessToken)
    }

    fun getExpire(tokenExpireSec: Long): NumericDate {
        // Calculate expire time and token.
        val expireAt = NumericDate.now()
        expireAt.addSeconds(if (tokenExpireSec <= 0) LocalLfsConfig.DEFAULT_TOKEN_EXPIRE_SEC else tokenExpireSec)
        return expireAt
    }

    enum class AuthMode {
        Anonymous, Username, External;

        companion object {
            fun find(value: String?): AuthMode? {
                for (authMode in values()) if (authMode.name.lowercase() == value) return authMode
                return null
            }
        }
    }
}
