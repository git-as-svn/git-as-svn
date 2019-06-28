/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.config;

import com.google.common.base.Strings;
import org.apache.commons.codec.binary.Hex;
import org.eclipse.jetty.server.Server;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;
import svnserver.ext.web.server.WebServer;
import svnserver.ext.web.token.EncryptionFactoryAes;

import java.net.URL;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;

/**
 * Web server configuration.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("web")
public final class WebServerConfig implements SharedConfig {
  @NotNull
  private final static String defaultSecret = generateDefaultSecret();

  @NotNull
  private List<ListenConfig> listen;
  @NotNull
  private String secret = defaultSecret;
  @Nullable
  private String baseUrl = null;

  public WebServerConfig() {
    listen = Collections.singletonList(new ListenHttpConfig());
  }

  public WebServerConfig(int port) {
    listen = Collections.singletonList(new ListenHttpConfig(port));
  }

  private static String generateDefaultSecret() {
    final SecureRandom random = new SecureRandom();
    final byte[] bytes = new byte[EncryptionFactoryAes.KEY_SIZE];
    random.nextBytes(bytes);
    return new String(Hex.encodeHex(bytes));
  }

  @Override
  public void create(@NotNull SharedContext context) throws Exception {
    final URL url;
    if (Strings.isNullOrEmpty(baseUrl)) {
      url = null;
    } else {
      url = new URL(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
    }

    context.add(WebServer.class, new WebServer(context, createJettyServer(), baseUrl == null ? null : url, new EncryptionFactoryAes(secret)));
  }

  @NotNull
  private Server createJettyServer() {
    final Server server = new Server();

    for (ListenConfig listenConfig : listen)
      server.addConnector(listenConfig.createConnector(server));

    return server;
  }
}
