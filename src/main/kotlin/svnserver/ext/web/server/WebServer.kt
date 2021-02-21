/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.server

import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.server.handler.RequestLogHandler
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.servlet.ServletMapping
import org.eclipse.jgit.util.Base64
import org.jose4j.jwe.JsonWebEncryption
import org.tmatesoft.svn.core.SVNException
import ru.bozaro.gitlfs.server.ServerError
import svnserver.Loggers
import svnserver.auth.User
import svnserver.auth.UserDB
import svnserver.context.Shared
import svnserver.context.SharedContext
import svnserver.ext.web.token.EncryptionFactory
import svnserver.ext.web.token.TokenHelper
import java.io.IOException
import java.io.StringWriter
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import javax.servlet.Servlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Web server component
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class WebServer(private val context: SharedContext, private val server: Server, baseUrl: URL?, tokenFactory: EncryptionFactory) : Shared {
    private val handler: ServletHandler?
    private val tokenFactory: EncryptionFactory
    private val servlets: MutableList<Holder> = CopyOnWriteArrayList()
    private var baseUrl: URI?

    @Throws(IOException::class)
    override fun init(context: SharedContext) {
        try {
            server.start()
            if (baseUrl == null) {
                for (connector in server.connectors) {
                    if (connector is ServerConnector) {
                        baseUrl = URI.create(String.format("http://%s:%s/", connector.host, connector.localPort))
                        break
                    }
                }
            }
            requireNotNull(baseUrl) { "Failed to determine baseUrl (no web connectors?)" }
        } catch (e: Exception) {
            throw IOException("Can't start http server", e)
        }
    }

    @Throws(Exception::class)
    override fun close() {
        server.stop()
        server.join()
    }

    fun addServlet(pathSpec: String, servlet: Servlet): Holder {
        log.info("Registered servlet for path: {}", pathSpec)
        val servletInfo = Holder(pathSpec, servlet)
        servlets.add(servletInfo)
        updateServlets()
        return servletInfo
    }

    private fun updateServlets() {
        if (handler != null) {
            val snapshot: Array<Holder> = servlets.toTypedArray()
            val holders = arrayOfNulls<ServletHolder>(snapshot.size)
            val mappings = arrayOfNulls<ServletMapping>(snapshot.size)
            for (i in snapshot.indices) {
                holders[i] = snapshot[i].holder
                mappings[i] = snapshot[i].mapping
            }
            handler.servlets = holders
            handler.servletMappings = mappings
        }
    }

    fun addServlets(servletMap: Map<String, Servlet>): Collection<Holder> {
        val servletInfos: MutableList<Holder> = ArrayList()
        for ((key, value) in servletMap) {
            log.info("Registered servlet for path: {}", key)
            val servletInfo = Holder(key, value)
            servletInfos.add(servletInfo)
        }
        servlets.addAll(servletInfos)
        updateServlets()
        return servletInfos
    }

    fun removeServlet(servletInfo: Holder) {
        if (servlets.remove(servletInfo)) {
            log.info("Unregistered servlet for path: {}", servletInfo.path)
            updateServlets()
        }
    }

    fun removeServlets(servletInfos: Collection<Holder>) {
        var modified = false
        for (servlet in servletInfos) {
            if (servlets.remove(servlet)) {
                log.info("Unregistered servlet for path: {}", servlet.path)
                modified = true
            }
        }
        if (modified) {
            updateServlets()
        }
    }

    /**
     * Return current user information.
     *
     * @param authorization HTTP authorization header value.
     * @return Return value:
     *
     *  * no authorization header - anonymous user;
     *  * invalid authorization header - null;
     *  * valid authorization header - user information.
     *
     */
    fun getAuthInfo(authorization: String?, tokenEnsureTime: Int): User? {
        val userDB = context.sure(UserDB::class.java)
        // Check HTTP authorization.
        if (authorization == null) {
            return User.anonymous
        }
        if (authorization.startsWith(AUTH_BASIC)) {
            val raw = String(Base64.decode(authorization.substring(AUTH_BASIC.length).trim { it <= ' ' }), StandardCharsets.UTF_8)
            val separator = raw.indexOf(':')
            if (separator > 0) {
                val username = raw.substring(0, separator)
                val password = raw.substring(separator + 1)
                try {
                    return userDB.check(username, password)
                } catch (e: SVNException) {
                    log.error("Authorization error: " + e.message, e)
                }
            }
            return null
        }
        return if (authorization.startsWith(AUTH_TOKEN)) {
            TokenHelper.parseToken(createEncryption(), authorization.substring(AUTH_TOKEN.length).trim { it <= ' ' }, tokenEnsureTime)
        } else null
    }

    fun createEncryption(): JsonWebEncryption {
        return tokenFactory.create()
    }

    fun getUrl(req: HttpServletRequest): URI {
        return getBaseUrl().resolve(req.requestURI)
    }

    fun getBaseUrl(): URI {
        return baseUrl!!
    }

    @Throws(MalformedURLException::class)
    fun toUrl(path: String): URL {
        return getBaseUrl().resolve(path).toURL()
    }

    @Throws(IOException::class)
    fun sendError(req: HttpServletRequest, resp: HttpServletResponse, error: ServerError) {
        resp.contentType = "text/html"
        resp.status = error.statusCode
        resp.writer.write(ErrorWriter(req).content(error))
    }

    private class ErrorWriter(private val req: HttpServletRequest) : ErrorHandler() {
        fun content(error: ServerError): String {
            return try {
                val writer = StringWriter()
                writeErrorPage(req, writer, error.statusCode, error.message, false)
                writer.toString()
            } catch (e: IOException) {
                e.message!!
            }
        }
    }

    inner class Holder constructor(val path: String, servlet: Servlet) {
        val holder: ServletHolder
        val mapping: ServletMapping

        init {
            holder = ServletHolder(servlet)
            mapping = ServletMapping()
            mapping.servletName = holder.name
            mapping.setPathSpec(path)
        }
    }

    companion object {
        const val AUTH_TOKEN = "Bearer "
        private const val AUTH_BASIC = "Basic "
        private val log = Loggers.web
    }

    init {
        this.baseUrl = baseUrl?.toURI()
        this.tokenFactory = tokenFactory
        val contextHandler = ServletContextHandler()
        contextHandler.contextPath = "/"
        handler = contextHandler.servletHandler
        val logHandler = RequestLogHandler()
        logHandler.requestLog = RequestLog { request: Request, response: Response ->
            val user = request.getAttribute(User::class.java.name) as User?
            val username = if (user == null || user.isAnonymous) "" else user.username
            log.info("{}:{} - {} - \"{} {}\" {} {}", request.remoteHost, request.remotePort, username, request.method, request.httpURI, response.status, response.reason)
        }
        val handlers = HandlerCollection()
        handlers.addHandler(contextHandler)
        handlers.addHandler(logHandler)
        server.handler = handlers
    }
}
