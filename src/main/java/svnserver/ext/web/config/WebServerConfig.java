/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.config;

import org.apache.commons.codec.binary.Hex;
import org.eclipse.jetty.server.Server;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;
import svnserver.ext.web.server.WebServer;
import svnserver.ext.web.token.EncryptionFactoryAes;

import java.io.IOException;
import java.security.SecureRandom;
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
  private final static String defaultSecret = generateDefaultSecret();

  @NotNull
  private List<ListenConfig> listen = new ArrayList<>();
  @NotNull
  private String realm = WebServer.DEFAULT_REALM;
  @NotNull
  private String secret = defaultSecret;
  @Nullable
  private String baseUrl = null;

  @NotNull
  public String getRealm() {
    return realm;
  }

  @Nullable
  public String getBaseUrl() {
    return baseUrl;
  }

  @NotNull
  public List<ListenConfig> getListen() {
    return listen;
  }

  @Override
  public void create(@NotNull SharedContext context) throws IOException {
    context.add(WebServer.class, new WebServer(context, createJettyServer(), this, new EncryptionFactoryAes(secret)));
  }

  @NotNull
  public Server createJettyServer() {
    final Server server = new Server();
    for (ListenConfig listenConfig : listen) {
      server.addConnector(listenConfig.createConnector(server));
    }
    return server;
  }

  private static String generateDefaultSecret() {
    final SecureRandom random = new SecureRandom();
    final byte bytes[] = new byte[EncryptionFactoryAes.KEY_SIZE];
    random.nextBytes(bytes);
    return new String(Hex.encodeHex(bytes));
  }
}
