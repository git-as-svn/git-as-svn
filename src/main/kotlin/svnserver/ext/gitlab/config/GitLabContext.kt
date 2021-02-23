/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.config

import com.google.api.client.auth.oauth.OAuthGetAccessToken
import com.google.api.client.auth.oauth2.PasswordTokenRequest
import com.google.api.client.auth.oauth2.TokenResponseException
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import org.gitlab.api.GitlabAPI
import org.gitlab.api.GitlabAPIException
import org.gitlab.api.TokenType
import org.gitlab.api.models.GitlabSession
import svnserver.context.Shared
import svnserver.context.SharedContext
import java.io.IOException
import java.net.HttpURLConnection

/**
 * GitLab context.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitLabContext internal constructor(val config: GitLabConfig) : Shared {
    @Throws(IOException::class)
    fun connect(username: String, password: String): GitlabSession {
        val token = obtainAccessToken(gitLabUrl, username, password, false)
        val api = connect(gitLabUrl, token)
        return api.currentSession
    }

    fun connect(): GitlabAPI {
        return Companion.connect(gitLabUrl, token)
    }

    val gitLabUrl: String
        get() = config.url
    val token: GitLabToken
        get() = config.getToken()
    val hookPath: String
        get() = config.hookPath

    companion object {
        private val transport: HttpTransport = NetHttpTransport()
        fun sure(context: SharedContext): GitLabContext {
            return context.sure(GitLabContext::class.java)
        }

        @Throws(IOException::class)
        fun obtainAccessToken(gitlabUrl: String, username: String, password: String, sudoScope: Boolean): GitLabToken {
            return try {
                val tokenServerUrl = OAuthGetAccessToken(gitlabUrl + "/oauth/token?scope=api" + if (sudoScope) "%20sudo" else "")
                val oauthResponse = PasswordTokenRequest(transport, JacksonFactory.getDefaultInstance(), tokenServerUrl, username, password).execute()
                GitLabToken(TokenType.ACCESS_TOKEN, oauthResponse.accessToken)
            } catch (e: TokenResponseException) {
                if (sudoScope && e.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    // Fallback for pre-10.2 gitlab versions
                    val session = GitlabAPI.connect(gitlabUrl, username, password)
                    GitLabToken(TokenType.PRIVATE_TOKEN, session.privateToken)
                } else {
                    throw GitlabAPIException(e.message, e.statusCode, e)
                }
            }
        }

        fun connect(gitlabUrl: String, token: GitLabToken): GitlabAPI {
            return GitlabAPI.connect(gitlabUrl, token.value, token.type)
        }
    }
}
