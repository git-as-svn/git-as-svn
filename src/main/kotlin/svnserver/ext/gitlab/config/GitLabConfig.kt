/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.config

import org.gitlab.api.TokenType
import ru.bozaro.gitlfs.client.auth.AuthProvider
import ru.bozaro.gitlfs.client.auth.BasicAuthProvider
import svnserver.auth.User
import svnserver.config.SharedConfig
import svnserver.context.LocalContext
import svnserver.context.SharedContext
import svnserver.ext.gitlfs.storage.BasicAuthHttpLfsStorage
import svnserver.ext.gitlfs.storage.LfsReader
import svnserver.ext.gitlfs.storage.LfsStorage
import svnserver.ext.gitlfs.storage.LfsStorageFactory
import java.io.IOException
import java.net.URI

/**
 * Gitlab access settings.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitLabConfig private constructor(var url: String, private var tokenType: TokenType, private var token: String) : SharedConfig {
    var hookPath = "_hooks/gitlab"
    private var lfsMode: LfsMode? = HttpLfsMode.instance
    var gitalyBinDir = "/opt/gitlab/embedded/bin"
    var gitalySocket = "/var/opt/gitlab/gitaly/gitaly.socket"
    var gitalyToken = "secret token"
    var glProtocol: GLProtocol = GLProtocol.Web

    constructor() : this("http://localhost/", TokenType.PRIVATE_TOKEN, "")
    constructor(url: String, token: GitLabToken) : this(url, token.type, token.value)

    override fun create(context: SharedContext) {
        val gitLabContext = GitLabContext(this)
        context.add(GitLabContext::class.java, gitLabContext)
        if (lfsMode != null) {
            context.add(LfsStorageFactory::class.java, object : LfsStorageFactory {
                override fun createStorage(context: LocalContext): LfsStorage {
                    return createLfsStorage(
                        url,
                        context.name,
                        "UNUSED", getToken().value,
                        lfsMode!!.readerFactory(context)
                    )
                }
            })
        }
    }

    fun getToken(): GitLabToken {
        return GitLabToken(tokenType, token)
    }

    companion object {
        fun createLfsStorage(
            gitLabUrl: String,
            repositoryName: String,
            username: String,
            password: String,
            readerFactory: LfsReaderFactory?
        ): LfsStorage {
            return object : BasicAuthHttpLfsStorage(gitLabUrl, repositoryName, username, password) {
                @Throws(IOException::class)
                override fun getReader(oid: String, size: Long): LfsReader? {
                    return if (readerFactory != null) readerFactory.createReader(oid) else super.getReader(oid, size)
                }

                override fun authProvider(user: User, baseURI: URI): AuthProvider {
                    val lfsCredentials = user.lfsCredentials ?: return super.authProvider(user, baseURI)
                    return BasicAuthProvider(baseURI, lfsCredentials.username, lfsCredentials.password)
                }
            }
        }
    }
}
