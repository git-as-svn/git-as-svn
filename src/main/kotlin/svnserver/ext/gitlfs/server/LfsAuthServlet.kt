/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server

import org.eclipse.jgit.util.StringUtils
import ru.bozaro.gitlfs.common.JsonHelper
import ru.bozaro.gitlfs.common.data.Link
import ru.bozaro.gitlfs.server.ServerError
import svnserver.context.LocalContext
import svnserver.ext.gitlfs.LfsAuthHelper
import svnserver.ext.gitlfs.LfsAuthHelper.AuthMode
import svnserver.ext.web.server.WebServer
import java.io.IOException
import java.net.URI
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * LFS storage pointer servlet.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal class LfsAuthServlet(private val context: LocalContext, private val baseLfsUrl: String, private val secretToken: String, private val tokenExpireSec: Long, private val tokenExpireTime: Float) : HttpServlet() {
    @Throws(IOException::class)
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        createToken(req, resp)
    }

    @Throws(IOException::class)
    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        createToken(req, resp)
    }

    @Throws(IOException::class)
    private fun createToken(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            val token = createToken(
                req,
                getUriParam(req, "url"),
                getStringParam(req, "secretToken"),
                getStringParam(req, "userId"),
                getStringParam(req, "mode")
            )
            resp.contentType = "application/json"
            JsonHelper.mapper.writeValue(resp.outputStream, token)
        } catch (e: ServerError) {
            webServer.sendError(req, resp, e)
        }
    }

    @Throws(ServerError::class)
    private fun createToken(
        req: HttpServletRequest,
        uri: URI?,
        secretToken: String?,
        userId: String?,
        mode: String?
    ): Link {
        // Check secretToken
        if (StringUtils.isEmptyOrNull(secretToken)) throw ServerError(HttpServletResponse.SC_BAD_REQUEST, "Parameter \"secretToken\" not specified")
        if (this.secretToken != secretToken) throw ServerError(HttpServletResponse.SC_FORBIDDEN, "Invalid secretToken")
        val authMode: AuthMode = AuthMode.find(mode) ?: throw ServerError(HttpServletResponse.SC_BAD_REQUEST, String.format("Unsupported mode: %s", mode))
        return LfsAuthHelper.createToken(context.shared, uri ?: webServer.getUrl(req).resolve(baseLfsUrl), userId, authMode, tokenExpireSec, tokenExpireTime)
    }

    private fun getUriParam(req: HttpServletRequest, name: String): URI? {
        val value = getStringParam(req, name) ?: return null
        return URI.create(value)
    }

    private val webServer: WebServer
        get() = context.shared.sure(WebServer::class.java)

    companion object {
        private fun getStringParam(req: HttpServletRequest, name: String): String? {
            return req.getParameter(name)
        }
    }
}
