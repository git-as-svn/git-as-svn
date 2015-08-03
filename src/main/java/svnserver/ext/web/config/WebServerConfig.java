/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.config;

import org.eclipse.jetty.server.Server;
import org.jetbrains.annotations.NotNull;
import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;
import svnserver.ext.web.server.WebServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Web server configuration.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("web")
public class WebServerConfig implements SharedConfig {
  @NotNull
  private List<ListenConfig> listen = new ArrayList<>();

  @NotNull
  public List<ListenConfig> getListen() {
    return listen;
  }

  @Override
  public void create(@NotNull SharedContext context) throws IOException {
    context.add(WebServer.class, new WebServer(createJettyServer()));
  }

  @NotNull
  public Server createJettyServer() {
    final Server server = new Server();
    for (ListenConfig listenConfig : listen) {
      server.addConnector(listenConfig.createConnector(server));
    }
    return server;
  }
}
