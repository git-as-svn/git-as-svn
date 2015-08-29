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
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.keys.AesKey;
import org.jose4j.lang.ByteUtil;
import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;
import svnserver.ext.web.server.WebServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
  private String realm = "Git as Subversion server";
  @NotNull
  private String secret = new String(Hex.encodeHex(ByteUtil.randomBytes(16)));

  @NotNull
  public List<ListenConfig> getListen() {
    return listen;
  }

  @Override
  public void create(@NotNull SharedContext context) throws IOException {
    context.add(WebServer.class, new WebServer(createJettyServer(), realm, () -> {
      final Key key = new AesKey(secretToKey(secret));
      final JsonWebEncryption jwe = new JsonWebEncryption();
      jwe.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.A128KW);
      jwe.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256);
      jwe.setKey(key);
      return jwe;
    }));
  }

  @NotNull
  public Server createJettyServer() {
    final Server server = new Server();
    for (ListenConfig listenConfig : listen) {
      server.addConnector(listenConfig.createConnector(server));
    }
    return server;
  }

  @NotNull
  private static byte[] secretToKey(@NotNull String secret) {
    try {
      return MessageDigest.getInstance("MD5").digest(secret.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
