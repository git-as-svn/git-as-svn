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
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import org.gitlab4j.api.Constants
import org.gitlab4j.api.GitLabApi
import svnserver.context.Shared
import svnserver.context.SharedContext
import java.io.IOException

/**
 * GitLab context.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitLabContext internal constructor(val config: GitLabConfig) : Shared {
    val api = connect(gitLabUrl, token)
    val gitLabUrl: String
        get() = config.url
    val token: GitLabToken
        get() = config.getToken()
    val hookPath: String
        get() = config.hookPath

    override fun close() {
        super.close()

        api.close()
    }

    companion object {
        private val transport: HttpTransport = NetHttpTransport()
        fun sure(context: SharedContext): GitLabContext {
            return context.sure(GitLabContext::class.java)
        }

        @Throws(IOException::class)
        fun login(gitlabUrl: String, username: String, password: String, sudoScope: Boolean): GitLabApi {
            val tokenServerUrl = OAuthGetAccessToken(gitlabUrl + "/oauth/token?scope=api" + if (sudoScope) "%20sudo" else "")
            val oauthResponse = PasswordTokenRequest(transport, JacksonFactory.getDefaultInstance(), tokenServerUrl, username, password).execute()
            return GitLabApi(gitlabUrl, Constants.TokenType.OAUTH2_ACCESS, oauthResponse.accessToken)
        }

        fun connect(gitlabUrl: String, token: GitLabToken): GitLabApi {
            return GitLabApi(gitlabUrl, token.type, token.value)
        }
    }
}
