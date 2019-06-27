/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.server;

import org.apache.http.HttpHeaders;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletMapping;
import org.eclipse.jgit.util.Base64;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jose4j.jwe.JsonWebEncryption;
import org.slf4j.Logger;
import org.tmatesoft.svn.core.SVNException;
import ru.bozaro.gitlfs.server.ServerError;
import svnserver.Loggers;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.context.Shared;
import svnserver.context.SharedContext;
import svnserver.ext.web.config.WebServerConfig;
import svnserver.ext.web.token.EncryptionFactory;
import svnserver.ext.web.token.TokenHelper;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Web server component
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class WebServer implements Shared {
  @NotNull
  public static final String AUTH_TOKEN = "Bearer ";
  @NotNull
  private static final String AUTH_BASIC = "Basic ";
  @NotNull
  private static final Logger log = Loggers.web;
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
  @NotNull
  private final List<Holder> servlets = new CopyOnWriteArrayList<>();

  public WebServer(@NotNull SharedContext context, @Nullable Server server, @NotNull WebServerConfig config, @NotNull EncryptionFactory tokenFactory) {
    this.context = context;
    this.server = server;
    this.config = config;
    this.tokenFactory = tokenFactory;
    if (server != null) {
      final ServletContextHandler contextHandler = new ServletContextHandler();
      contextHandler.setContextPath("/");
      handler = contextHandler.getServletHandler();

      final RequestLogHandler logHandler = new RequestLogHandler();
      logHandler.setRequestLog((request, response) -> {
        final User user = (User) request.getAttribute(User.class.getName());
        final String userName = (user == null || user.isAnonymous()) ? "" : user.getUserName();
        log.info("{}:{} - {} - \"{} {}\" {} {}", request.getRemoteHost(), request.getRemotePort(), userName, request.getMethod(), request.getHttpURI(), response.getStatus(), response.getReason());
      });

      final HandlerCollection handlers = new HandlerCollection();
      handlers.addHandler(contextHandler);
      handlers.addHandler(logHandler);
      server.setHandler(handlers);
    } else {
      handler = null;
    }
  }

  @NotNull
  public static WebServer get(@NotNull SharedContext context) {
    return context.getOrCreate(WebServer.class, () -> new WebServer(context, null, new WebServerConfig(), JsonWebEncryption::new));
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

  @Override
  public void close() throws Exception {
    if (server != null) {
      server.stop();
      server.join();
    }
  }

  @NotNull
  public Holder addServlet(@NotNull String pathSpec, @NotNull Servlet servlet) {
    log.info("Registered servlet for path: {}", pathSpec);
    final Holder servletInfo = new Holder(pathSpec, servlet);
    servlets.add(servletInfo);
    updateServlets();
    return servletInfo;
  }

  private void updateServlets() {
    if (handler != null) {
      final Holder[] snapshot = servlets.toArray(new Holder[0]);
      final ServletHolder[] holders = new ServletHolder[snapshot.length];
      final ServletMapping[] mappings = new ServletMapping[snapshot.length];
      for (int i = 0; i < snapshot.length; ++i) {
        holders[i] = snapshot[i].holder;
        mappings[i] = snapshot[i].mapping;
      }
      handler.setServlets(holders);
      handler.setServletMappings(mappings);
    }
  }

  @NotNull
  public Collection<Holder> addServlets(@NotNull Map<String, Servlet> servletMap) {
    List<Holder> servletInfos = new ArrayList<>();
    for (Map.Entry<String, Servlet> entry : servletMap.entrySet()) {
      log.info("Registered servlet for path: {}", entry.getKey());
      final Holder servletInfo = new Holder(entry.getKey(), entry.getValue());
      servletInfos.add(servletInfo);
    }
    servlets.addAll(servletInfos);
    updateServlets();
    return servletInfos;
  }

  public void removeServlet(@NotNull Holder servletInfo) {
    if (servlets.remove(servletInfo)) {
      log.info("Unregistered servlet for path: {}", servletInfo.path);
      updateServlets();
    }
  }

  public void removeServlets(@NotNull Collection<Holder> servletInfos) {
    boolean modified = false;
    for (Holder servlet : servletInfos) {
      if (servlets.remove(servlet)) {
        log.info("Unregistered servlet for path: {}", servlet.path);
        modified = true;
      }
    }
    if (modified) {
      updateServlets();
    }
  }

  /**
   * Return current user information.
   *
   * @param authorization HTTP authorization header value.
   * @return Return value:
   * <ul>
   * <li>no authorization header - anonymous user;</li>
   * <li>invalid authorization header - null;</li>
   * <li>valid authorization header - user information.</li>
   * </ul>
   */
  @Nullable
  public User getAuthInfo(@Nullable final String authorization, int tokenEnsureTime) {
    final UserDB userDB = context.sure(UserDB.class);
    // Check HTTP authorization.
    if (authorization == null) {
      return User.getAnonymous();
    }
    if (authorization.startsWith(AUTH_BASIC)) {
      final String raw = new String(Base64.decode(authorization.substring(AUTH_BASIC.length()).trim()), StandardCharsets.UTF_8);
      final int separator = raw.indexOf(':');
      if (separator > 0) {
        final String username = raw.substring(0, separator);
        final String password = raw.substring(separator + 1);
        try {
          return userDB.check(username, password);
        } catch (SVNException e) {
          log.error("Authorization error: " + e.getMessage(), e);
        }
      }
      return null;
    }
    if (authorization.startsWith(AUTH_TOKEN)) {
      return TokenHelper.parseToken(createEncryption(), authorization.substring(AUTH_TOKEN.length()).trim(), tokenEnsureTime);
    }
    return null;
  }

  @NotNull
  public JsonWebEncryption createEncryption() {
    return tokenFactory.create();
  }

  @NotNull
  public URI getUrl(@NotNull HttpServletRequest req) {
    if (config.getBaseUrl() != null)
      return URI.create(config.getBaseUrl()).resolve(req.getRequestURI());

    String host = req.getHeader(HttpHeaders.HOST);
    if (host == null)
      host = req.getServerName() + ":" + req.getServerPort();

    return URI.create(req.getScheme() + "://" + host + req.getRequestURI());
  }

  @NotNull
  public URL getUrl(@NotNull URL baseUri) throws MalformedURLException {
    if (config.getBaseUrl() != null)
      return URI.create(config.getBaseUrl()).resolve(baseUri.getPath()).toURL();

    return baseUri;
  }

  public void sendError(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp, @NotNull ServerError error) throws IOException {
    resp.setContentType("text/html");
    resp.setStatus(error.getStatusCode());
    resp.getWriter().write(new ErrorWriter(req).content(error));
  }

  private static class ErrorWriter extends ErrorHandler {

    private final HttpServletRequest req;

    private ErrorWriter(HttpServletRequest req) {
      this.req = req;
    }

    @NotNull
    public String content(@NotNull ServerError error) {
      try {
        final StringWriter writer = new StringWriter();
        writeErrorPage(req, writer, error.getStatusCode(), error.getMessage(), false);
        return writer.toString();
      } catch (IOException e) {
        return e.getMessage();
      }
    }
  }

  public final class Holder {
    @NotNull
    private final String path;
    @NotNull
    private final ServletHolder holder;
    @NotNull
    private final ServletMapping mapping;

    private Holder(@NotNull String pathSpec, @NotNull Servlet servlet) {
      path = pathSpec;
      holder = new ServletHolder(servlet);
      mapping = new ServletMapping();
      mapping.setServletName(holder.getName());
      mapping.setPathSpec(pathSpec);
    }
  }
}
