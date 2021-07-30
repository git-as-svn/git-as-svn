/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.config

import io.gitea.ApiClient
import io.gitea.auth.ApiKeyAuth
import svnserver.context.Shared
import svnserver.context.SharedContext

/**
 * Gitea context.
 *
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
class GiteaContext internal constructor(private val config: GiteaConfig) : Shared {
    fun connect(username: String, password: String): ApiClient {
        val apiClient = ApiClient()
        apiClient.basePath = config.url
        apiClient.setUsername(username)
        apiClient.setPassword(password)
        return apiClient
    }

    fun connect(username: String): ApiClient {
        val apiClient = connect()
        val sudoParam = apiClient.getAuthentication("SudoParam") as ApiKeyAuth
        sudoParam.apiKey = username
        return apiClient
    }

    val token: GiteaToken
        get() = config.getToken()

    private val giteaUrl: String
        get() = config.url

    fun connect(giteaUrl: String = this.giteaUrl, token: GiteaToken = this.token): ApiClient {
        return Companion.connect(giteaUrl, this.token)
    }

    companion object {
        fun sure(context: SharedContext): GiteaContext {
            return context.sure(GiteaContext::class.java)
        }

        fun connect(giteaUrl: String, token: GiteaToken): ApiClient {
            val apiClient = ApiClient()
            apiClient.basePath = giteaUrl
            val auth = apiClient.getAuthentication("AuthorizationHeaderToken")
            if (auth is ApiKeyAuth) {
                auth.apiKey = token.value
                auth.apiKeyPrefix = "token"
            }
            return apiClient
        }
    }
}
