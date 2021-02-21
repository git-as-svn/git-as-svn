/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.config

import ru.bozaro.gitlfs.client.auth.AuthProvider
import ru.bozaro.gitlfs.client.auth.BasicAuthProvider
import svnserver.auth.User
import svnserver.config.SharedConfig
import svnserver.context.LocalContext
import svnserver.context.SharedContext
import svnserver.ext.gitlfs.storage.BasicAuthHttpLfsStorage
import svnserver.ext.gitlfs.storage.LfsStorage
import svnserver.ext.gitlfs.storage.LfsStorageFactory
import java.net.URI

/**
 * Gitea access settings.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class GiteaConfig private constructor(var url: String, private var token: String) : SharedConfig {
    private var lfs = true

    constructor() : this("http://localhost/", "")
    constructor(url: String, token: GiteaToken) : this(url, token.value)

    override fun create(context: SharedContext) {
        val giteaContext = GiteaContext(this)
        context.add(GiteaContext::class.java, giteaContext)
        if (lfs) {
            context.add(LfsStorageFactory::class.java, object : LfsStorageFactory {
                override fun createStorage(context: LocalContext): LfsStorage {
                    return createLfsStorage(url, context.name, getToken())
                }
            })
        }
    }

    fun getToken(): GiteaToken {
        return GiteaToken(token)
    }

    companion object {
        fun createLfsStorage(giteaUrl: String, repositoryName: String, token: GiteaToken): LfsStorage {
            return object : BasicAuthHttpLfsStorage(giteaUrl, repositoryName, token.value, "x-oauth-basic") {
                override fun authProvider(user: User, baseURI: URI): AuthProvider {
                    val lfsCredentials = user.lfsCredentials ?: return super.authProvider(user, baseURI)
                    return BasicAuthProvider(baseURI, lfsCredentials.username, lfsCredentials.password)
                }
            }
        }
    }
}
