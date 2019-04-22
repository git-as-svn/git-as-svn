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
  private int port = 80;
  private boolean forwarded = false;

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
    http.setIdleTimeout(30000);
    return http;
  }
}
