/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.auth

import org.gitlab4j.api.GitLabApiException
import svnserver.Loggers
import svnserver.UserType
import svnserver.auth.Authenticator
import svnserver.auth.PlainAuthenticator
import svnserver.auth.User
import svnserver.auth.User.LfsCredentials
import svnserver.auth.UserDB
import svnserver.context.SharedContext
import svnserver.ext.gitlab.config.GitLabContext
import svnserver.ext.gitlab.config.GitLabToken
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import org.gitlab4j.api.models.User as GitLabUser

/**
 * GitLab user authentiation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitLabUserDB(private val config: GitLabUserDBConfig, context: SharedContext) : UserDB {
    private val context: GitLabContext = context.sure(GitLabContext::class.java)

    override val authenticators: Collection<Authenticator> = setOf(PlainAuthenticator(this))

    override fun check(username: String, password: String): User? {
        return try {
            config.authentication.login(context.gitLabUrl, username, password).use {
                val session = it.userApi.currentUser
                if (session.username == username) {
                    createUser(session, password)
                } else {
                    // This can happen when user authenticates using access token (so username is not used) but enters wrong username.
                    // While we properly calculate username, svn *client* thinks that their username is what user has entered.
                    log.warn("User password check error: expected username=${session.username} but got username=$username")
                    null
                }
            }
        } catch (e: GitLabApiException) {
            if (e.httpStatus == HttpURLConnection.HTTP_UNAUTHORIZED) {
                return null
            }
            log.warn("User password check error: $username", e)
            null
        } catch (e: IOException) {
            log.warn("User password check error: $username", e)
            null
        }
    }

    private fun createUser(user: GitLabUser, password: String?): User {
        return User.create(user.username, user.name, user.email, user.id.toString(), UserType.GitLab, if (password == null) null else LfsCredentials(user.username, password))
    }

    override fun lookupByUserName(username: String): User? {
        return try {
            val user = context.api.duplicate().use {
                it.sudo(username)
                it.userApi.currentUser
            }
            createUser(user, null)
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IOException) {
            log.warn("User lookup by name error: $username", e)
            null
        }
    }

    override fun lookupByExternal(external: String): User? {
        val userId = removePrefix(external, PREFIX_USER)
        if (userId != null) {
            return try {
                createUser(context.api.userApi.getUser(userId), null)
            } catch (e: FileNotFoundException) {
                null
            } catch (e: IOException) {
                log.warn("User lookup by userId error: $external", e)
                null
            }
        }
        val keyId = removePrefix(external, PREFIX_KEY)
        return if (keyId != null) {
            try {
                createUser(context.api.keysAPI.getUserBySSHKeyFingerprint(keyId.toString()).user, null)
            } catch (e: FileNotFoundException) {
                null
            } catch (e: IOException) {
                log.warn("User lookup by SSH key error: $external", e)
                null
            }
        } else null
    }

    private fun removePrefix(glId: String, prefix: String): Long? {
        if (glId.startsWith(prefix)) {
            var result = 0L
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
        private val log = Loggers.gitlab
        const val PREFIX_USER = "user-"
        private const val PREFIX_KEY = "key-"
    }
}
