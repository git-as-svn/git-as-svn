/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.server;

import org.apache.http.HttpHeaders;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.util.Base64;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jose4j.jwe.JsonWebEncryption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.context.Shared;
import svnserver.context.SharedContext;
import svnserver.ext.web.config.WebServerConfig;
import svnserver.ext.web.token.EncryptionFactory;
import svnserver.ext.web.token.TokenHelper;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Web server component
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class WebServer implements Shared {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(WebServer.class);

  @NotNull
  public static final String DEFAULT_REALM = "Git as Subversion server";
  @NotNull
  public static final String AUTH_BASIC = "Basic ";
  @NotNull
  public static final String AUTH_TOKEN = "Token ";

  @NotNull
  private final SharedContext context;
  @Nullable
  private final Server server;
  @Nullable
  private final ServletHandler handler;
  @NotNull
  private final WebServerConfig config;
  @NotNull
  private final EncryptionFactory tokenFactory;

  public WebServer(@NotNull SharedContext context, @Nullable Server server, @NotNull WebServerConfig config, @NotNull EncryptionFactory tokenFactory) {
    this.context = context;
    this.server = server;
    this.config = config;
    this.tokenFactory = tokenFactory;
    if (server != null) {
      handler = new ServletHandler();
      server.setHandler(handler);
    } else {
      handler = null;
    }
  }

  @NotNull
  public String getRealm() {
    return config.getRealm();
  }

  @NotNull
  public JsonWebEncryption createEncryption() {
    return tokenFactory.create();
  }

  @Override
  public void ready(@NotNull SharedContext context) throws IOException {
    try {
      if (server != null) {
        server.start();
      }
    } catch (Exception e) {
      throw new IOException("Can't start http server", e);
    }
  }

  public void addServlet(@NotNull String pathSpec, @NotNull Servlet servlet) {
    if (handler != null) {
      log.info("Registered servlet for path: {}", pathSpec);
      handler.addServletWithMapping(new ServletHolder(servlet), pathSpec);
    }
  }

  public void removeServlet(@NotNull String pathSpec) {
    // todo: Add remove servlet by pathSpec
  }

  @Override
  public void close() throws Exception {
    if (server != null) {
      server.stop();
      server.join();
    }
  }

  public static WebServer get(@NotNull SharedContext context) throws IOException, SVNException {
    return context.getOrCreate(WebServer.class, () -> new WebServer(context, null, new WebServerConfig(), JsonWebEncryption::new));
  }

  @Nullable
  public User getAuthInfo(@NotNull HttpServletRequest req) {
    final UserDB userDB = context.sure(UserDB.class);
    // Check HTTP authorization.
    final String authorization = req.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null) {
      return null;
    }
    if (authorization.startsWith(AUTH_BASIC)) {
      final String raw = new String(Base64.decode(authorization.substring(AUTH_BASIC.length())), StandardCharsets.UTF_8);
      final int separator = raw.indexOf(':');
      if (separator > 0) {
        final String username = raw.substring(0, separator);
        final String password = raw.substring(separator + 1);
        try {
          return userDB.check(username, password);
        } catch (IOException | SVNException e) {
          log.error("Authorization error: " + e.getMessage(), e);
        }
      }
      return null;
    }
    if (authorization.startsWith(AUTH_TOKEN)) {
      return TokenHelper.parseToken(createEncryption(), authorization.substring(AUTH_TOKEN.length()));
    }
    return null;
  }

  @NotNull
  public String getUrl(@NotNull HttpServletRequest req) {
    if (config.getBaseUrl() != null) {
      return URI.create(config.getBaseUrl()).resolve(req.getRequestURI()).toString();
    }
    String host = req.getHeader(HttpHeaders.HOST);
    if (host == null) {
      host = req.getServerName() + ":" + req.getServerPort();
    }
    return req.getScheme() + "://" + host + req.getRequestURI();
  }
}
