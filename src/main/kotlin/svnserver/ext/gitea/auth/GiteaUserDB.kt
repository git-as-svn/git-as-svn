/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.auth

import io.gitea.ApiException
import io.gitea.api.UserApi
import io.gitea.model.User
import svnserver.Loggers
import svnserver.UserType
import svnserver.auth.Authenticator
import svnserver.auth.PlainAuthenticator
import svnserver.auth.User.LfsCredentials
import svnserver.auth.UserDB
import svnserver.context.SharedContext
import svnserver.ext.gitea.config.GiteaContext
import java.net.HttpURLConnection

/**
 * Gitea user authentiation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
class GiteaUserDB internal constructor(context: SharedContext) : UserDB {
    private val authenticators: Collection<Authenticator> = setOf(PlainAuthenticator(this))
    private val context: GiteaContext = context.sure(GiteaContext::class.java)
    override fun authenticators(): Collection<Authenticator> {
        return authenticators
    }

    override fun check(username: String, password: String): svnserver.auth.User? {
        return try {
            val apiClient = context.connect(username, password)
            val userApi = UserApi(apiClient)
            createUser(userApi.userGetCurrent(), password)
        } catch (e: ApiException) {
            if (e.code == HttpURLConnection.HTTP_UNAUTHORIZED || e.code == HttpURLConnection.HTTP_FORBIDDEN) {
                return null
            }
            log.warn("User password check error: $username", e)
            null
        }
    }

    private fun createUser(user: User, password: String?): svnserver.auth.User {
        return svnserver.auth.User.create(user.login, if (user.fullName.isNullOrEmpty()) user.login else user.fullName, user.email, user.id.toString(), UserType.Gitea, if (password == null) null else LfsCredentials(user.login, password))
    }

    override fun lookupByUserName(username: String): svnserver.auth.User? {
        val apiClient = context.connect()
        return try {
            val userApi = UserApi(apiClient)
            val user = userApi.userGet(username)
            createUser(user, null)
        } catch (e: ApiException) {
            if (e.code != HttpURLConnection.HTTP_NOT_FOUND) {
                log.warn("User lookup by name error: $username", e)
            }
            null
        }
    }

    override fun lookupByExternal(external: String): svnserver.auth.User? {
        val userId = removePrefix(external, PREFIX_USER)
        if (userId != null) {
            try {
                val userApi = UserApi(context.connect())
                val users = userApi.userSearch(null, userId, null, null)
                for (u in users.data) {
                    if (userId == u.id) {
                        log.info("Matched {} with {}", external, u.login)
                        return createUser(u, null)
                    }
                }
            } catch (e: ApiException) {
                if (e.code != HttpURLConnection.HTTP_NOT_FOUND) {
                    log.warn("User lookup by userId error: $external", e)
                }
                return null
            }
        }
        val keyId = removePrefix(external, PREFIX_KEY)
        if (keyId != null) {
            return try {
                val userApi = UserApi(context.connect())
                val key = userApi.userCurrentGetKey(keyId)
                if (key.user != null) {
                    log.info("Matched {} with {}", external, key.user.login)
                    createUser(key.user, null)
                } else {
                    log.info("Matched {} with a key, but no User is associated.", external)
                    null
                }
            } catch (e: ApiException) {
                if (e.code != HttpURLConnection.HTTP_NOT_FOUND) {
                    log.warn("User lookup by userId error: $external", e)
                }
                null
            }
        }
        log.info("Unable to match {}", external)
        return null
    }

    private fun removePrefix(glId: String, prefix: String): Long? {
        if (glId.startsWith(prefix)) {
            var result: Long = 0
            for (i in prefix.length until glId.length) {
                val c = glId[i]
                if (c < '0' || c > '9') return null
                result = result * 10 + (c - '0')
            }
            return result
        }
        return null
    }

    companion object {
        private val log = Loggers.gitea
        private const val PREFIX_USER = "user-"
        private const val PREFIX_KEY = "key-"
    }
}
