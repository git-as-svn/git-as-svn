/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.auth

import org.gitlab.api.GitlabAPI
import org.gitlab.api.GitlabAPIException
import org.gitlab.api.models.GitlabUser
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

/**
 * GitLab user authentiation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitLabUserDB internal constructor(private val config: GitLabUserDBConfig, context: SharedContext) : UserDB {
    private val authenticators: Collection<Authenticator> = setOf(PlainAuthenticator(this))
    private val context: GitLabContext = context.sure(GitLabContext::class.java)
    override fun authenticators(): Collection<Authenticator> {
        return authenticators
    }

    override fun check(username: String, password: String): User? {
        return try {
            val token: GitLabToken = config.authentication.obtainAccessToken(context.gitLabUrl, username, password)
            val api: GitlabAPI = GitLabContext.connect(context.gitLabUrl, token)
            val session = api.currentSession
            createUser(session, password)
        } catch (e: GitlabAPIException) {
            if (e.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                return null
            }
            log.warn("User password check error: $username", e)
            null
        } catch (e: IOException) {
            log.warn("User password check error: $username", e)
            null
        }
    }

    private fun createUser(user: GitlabUser, password: String?): User {
        return User.create(user.username, user.name, user.email, user.id.toString(), UserType.GitLab, if (password == null) null else LfsCredentials(user.username, password))
    }

    override fun lookupByUserName(username: String): User? {
        return try {
            createUser(context.connect().getUserViaSudo(username), null)
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
                createUser(context.connect().getUser(userId), null)
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
                createUser(context.connect().getSSHKey(keyId).user, null)
            } catch (e: FileNotFoundException) {
                null
            } catch (e: IOException) {
                log.warn("User lookup by SSH key error: $external", e)
                null
            }
        } else null
    }

    private fun removePrefix(glId: String, prefix: String): Int? {
        if (glId.startsWith(prefix)) {
            var result = 0
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
