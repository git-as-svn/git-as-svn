/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage

import org.apache.http.impl.client.CloseableHttpClient
import org.eclipse.jgit.lib.Constants
import ru.bozaro.gitlfs.client.Client
import ru.bozaro.gitlfs.client.auth.AuthProvider
import ru.bozaro.gitlfs.client.auth.BasicAuthProvider
import svnserver.auth.User
import svnserver.ext.gitlfs.server.LfsServer
import svnserver.ext.gitlfs.storage.network.LfsHttpStorage
import java.net.URI

/**
 * HTTP remote storage for LFS files.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
open class BasicAuthHttpLfsStorage(baseUrl: String, repositoryName: String, username: String, password: String) : LfsHttpStorage() {
    private val httpClient: CloseableHttpClient = createHttpClient()
    private val baseURI: URI = buildAuthURI(baseUrl, repositoryName)
    private val fallbackAuthProvider: BasicAuthProvider = BasicAuthProvider(baseURI, username, password)
    override fun lfsClient(user: User): Client {
        return Client(authProvider(user, baseURI), httpClient)
    }

    protected open fun authProvider(user: User, baseURI: URI): AuthProvider {
        return fallbackAuthProvider
    }

    companion object {
        protected fun buildAuthURI(_baseUrl: String, repositoryName: String): URI {
            var baseUrl = _baseUrl
            if (!baseUrl.endsWith("/")) baseUrl += "/"
            return URI.create(baseUrl + repositoryName + Constants.DOT_GIT_EXT + "/" + LfsServer.SERVLET_BASE)
        }
    }
}
