/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server

import ru.bozaro.gitlfs.server.ContentManager
import ru.bozaro.gitlfs.server.ContentServlet
import ru.bozaro.gitlfs.server.LocksServlet
import ru.bozaro.gitlfs.server.PointerServlet
import svnserver.context.Local
import svnserver.context.LocalContext
import svnserver.context.Shared
import svnserver.ext.gitlfs.LocalLfsConfig
import svnserver.ext.gitlfs.storage.LfsStorage
import svnserver.ext.web.server.WebServer
import kotlin.math.max
import kotlin.math.min

/**
 * LFS server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class LfsServer(private val secretToken: String, tokenExpireSec: Long, tokenEnsureTime: Float) : Shared {
    private val tokenExpireSec: Long = if (tokenExpireSec > 0) tokenExpireSec else LocalLfsConfig.DEFAULT_TOKEN_EXPIRE_SEC
    private val tokenEnsureTime: Float = max(0.0f, min(tokenEnsureTime, 1.0f))
    fun register(localContext: LocalContext, storage: LfsStorage) {
        val webServer = localContext.shared.sure(WebServer::class.java)
        val name = localContext.name
        val pathSpec = String.format("/%s.git/", name).replace("/+".toRegex(), "/")
        val pointerManager: ContentManager = LfsContentManager(localContext, storage, tokenExpireSec, tokenEnsureTime)
        val contentManager = LfsContentManager(localContext, storage, tokenExpireSec, 0.0f)
        val servletsInfo = webServer.addServlets(
            mapOf(
                pathSpec + SERVLET_AUTH to LfsAuthServlet(localContext, pathSpec + SERVLET_BASE, secretToken, tokenExpireSec, tokenEnsureTime),
                "$pathSpec$SERVLET_POINTER/*" to PointerServlet(pointerManager, pathSpec + SERVLET_CONTENT),
                "$pathSpec$SERVLET_CONTENT/*" to ContentServlet(contentManager),
                pathSpec + SERVLET_BASE + "locks/*" to LocksServlet(LfsLockManager(contentManager)),
            )
        )
        localContext.add(LfsServerHolder::class.java, LfsServerHolder(webServer, servletsInfo))
    }

    fun unregister(localContext: LocalContext) {
        val holder = localContext.remove(LfsServerHolder::class.java)
        holder?.close()
    }

    private class LfsServerHolder(private val webServer: WebServer, private val servlets: Collection<WebServer.Holder>) : Local {
        override fun close() {
            webServer.removeServlets(servlets)
        }
    }

    companion object {
        const val SERVLET_BASE = "info/lfs/"
        const val SERVLET_AUTH = "lfs_authenticate"
        private const val SERVLET_CONTENT = SERVLET_BASE + "storage"
        private const val SERVLET_POINTER = SERVLET_BASE + "objects"
    }
}
