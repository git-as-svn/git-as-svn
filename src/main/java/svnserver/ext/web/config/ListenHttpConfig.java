/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.config;

import org.eclipse.jetty.server.*;
import org.jetbrains.annotations.NotNull;
import svnserver.config.serializer.ConfigType;

/**
 * HTTP listen config
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("http")
public final class ListenHttpConfig implements ListenConfig {
  @NotNull
  private String host = "localhost";
  private int port;
  private boolean forwarded = false;
  private long idleTimeout = -1;

  public ListenHttpConfig() {
    this(8123);
  }

  public ListenHttpConfig(int port) {
    this.port = port;
  }

  @NotNull
  @Override
  public Connector createConnector(@NotNull Server server) {
    final HttpConfiguration config = new HttpConfiguration();
    if (forwarded) {
      config.addCustomizer(new ForwardedRequestCustomizer());
    }
    final ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(config));
    http.setPort(port);
    http.setHost(host);

    if (idleTimeout >= 0)
      http.setIdleTimeout(idleTimeout);

    return http;
  }
}
