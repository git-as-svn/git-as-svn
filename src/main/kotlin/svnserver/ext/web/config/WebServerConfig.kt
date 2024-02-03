/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.config

import com.google.common.base.Strings
import org.apache.commons.codec.binary.Hex
import org.eclipse.jetty.server.Server
import svnserver.config.SharedConfig
import svnserver.context.SharedContext
import svnserver.ext.web.server.WebServer
import svnserver.ext.web.token.EncryptionFactoryAes
import java.net.URL
import java.security.SecureRandom

/**
 * Web server configuration.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class WebServerConfig : SharedConfig {
    private var listen: Array<ListenConfig>
    private var secret = defaultSecret
    private var baseUrl: String? = null

    constructor() {
        listen = arrayOf(ListenHttpConfig())
    }

    constructor(port: Int) {
        listen = arrayOf(ListenHttpConfig(port))
    }

    @Throws(Exception::class)
    override fun create(context: SharedContext) {
        val url: URL? = if (Strings.isNullOrEmpty(baseUrl)) {
            null
        } else {
            URL(if (baseUrl!!.endsWith("/")) baseUrl else "$baseUrl/")
        }
        context.add(WebServer::class.java, WebServer(context, createJettyServer(), if (baseUrl == null) null else url, EncryptionFactoryAes(secret)))
    }

    private fun createJettyServer(): Server {
        // TODO: Make executor configurable
        val server = Server()
        for (listenConfig in listen) server.addConnector(listenConfig.createConnector(server))
        return server
    }

    companion object {
        private val defaultSecret = generateDefaultSecret()
        private fun generateDefaultSecret(): String {
            val random = SecureRandom()
            val bytes = ByteArray(EncryptionFactoryAes.KEY_SIZE)
            random.nextBytes(bytes)
            return String(Hex.encodeHex(bytes))
        }
    }
}
