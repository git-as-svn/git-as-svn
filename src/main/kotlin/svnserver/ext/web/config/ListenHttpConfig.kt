/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.config

import org.eclipse.jetty.server.*

/**
 * HTTP listen config
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class ListenHttpConfig @JvmOverloads constructor(private var port: Int = 8123) : ListenConfig {
    private var host = "localhost"
    private var forwarded = false
    private var idleTimeout: Long = -1
    override fun createConnector(server: Server): Connector {
        val config = HttpConfiguration()
        if (forwarded) {
            config.addCustomizer(ForwardedRequestCustomizer())
        }
        val http = ServerConnector(server, HttpConnectionFactory(config))
        http.port = port
        http.host = host
        if (idleTimeout >= 0) http.idleTimeout = idleTimeout
        return http
    }
}
