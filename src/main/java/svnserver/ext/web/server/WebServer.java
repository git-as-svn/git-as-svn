/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletMapping;
import org.eclipse.jgit.util.Base64;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jose4j.jwe.JsonWebEncryption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.context.LocalContext;
import svnserver.context.Shared;
import svnserver.context.SharedContext;
import svnserver.ext.web.config.WebServerConfig;
import svnserver.ext.web.token.EncryptionFactory;
import svnserver.ext.web.token.TokenHelper;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.net.URI;
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
public class WebServer implements Shared {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(WebServer.class);

  @NotNull
  public static final String DEFAULT_REALM = "Git as Subversion server";
  @NotNull
  public static final String AUTH_BASIC = "Basic ";
  @NotNull
  public static final String AUTH_TOKEN = "Bearer ";

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
  private final List<ServletInfo> servlets = new CopyOnWriteArrayList<>();

  public WebServer(@NotNull SharedContext context, @Nullable Server server, @NotNull WebServerConfig config, @NotNull EncryptionFactory tokenFactory) {
    this.context = context;
    this.server = server;
    this.config = config;
    this.tokenFactory = tokenFactory;
    if (server != null) {
      final ServletContextHandler contextHandler = new ServletContextHandler();
      contextHandler.setContextPath("/");
      handler = contextHandler.getServletHandler();

      //final ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
      //securityHandler.addConstraintMapping(new );
      //contextHandler.setSecurityHandler(securityHandler);

      final RequestLogHandler logHandler = new RequestLogHandler();
      logHandler.setRequestLog(new RequestLog() {
        @Override
        public void log(Request request, Response response) {
          final User user = (User) request.getAttribute(User.class.getName());
          final String userName = (user == null || user.isAnonymous()) ? "" : user.getUserName();
          log.info("{}:{} - {} - \"{} {}\" {} {}", request.getRemoteHost(), request.getRemotePort(), userName, request.getMethod(), request.getHttpURI(), response.getStatus(), response.getReason());
        }
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

  @NotNull
  public ServletInfo addServlet(@NotNull String pathSpec, @NotNull Servlet servlet) {
    log.info("Registered servlet for path: {}", pathSpec);
    final ServletInfo servletInfo = new ServletInfo(pathSpec, servlet);
    servlets.add(servletInfo);
    updateServlets();
    return servletInfo;
  }

  @NotNull
  public Collection<ServletInfo> addServlets(@NotNull Map<String, Servlet> servletMap) {
    List<ServletInfo> servletInfos = new ArrayList<>();
    for (Map.Entry<String, Servlet> entry : servletMap.entrySet()) {
      log.info("Registered servlet for path: {}", entry.getKey());
      final ServletInfo servletInfo = new ServletInfo(entry.getKey(), entry.getValue());
      servletInfos.add(servletInfo);
    }
    servlets.addAll(servletInfos);
    updateServlets();
    return servletInfos;
  }

  public void removeServlet(@NotNull ServletInfo servletInfo) {
    if (servlets.remove(servletInfo)) {
      log.info("Unregistered servlet for path: {}", servletInfo.path);
      updateServlets();
    }
  }

  public void removeServlets(@NotNull Collection<ServletInfo> servletInfos) {
    boolean modified = false;
    for (ServletInfo servlet : servletInfos) {
      if (servlets.remove(servlet)) {
        log.info("Unregistered servlet for path: {}", servlet.path);
        modified = true;
      }
    }
    if (modified) {
      updateServlets();
    }
  }

  private void updateServlets() {
    if (handler != null) {
      final ServletInfo[] snapshot = servlets.toArray(new ServletInfo[servlets.size()]);
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
  public User getAuthInfo(@Nullable final String authorization) {
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
        } catch (IOException | SVNException e) {
          log.error("Authorization error: " + e.getMessage(), e);
        }
      }
      return null;
    }
    if (authorization.startsWith(AUTH_TOKEN)) {
      return TokenHelper.parseToken(createEncryption(), authorization.substring(AUTH_TOKEN.length()).trim());
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

  @NotNull
  public URI getUrl(@NotNull URI baseUri) {
    if (config.getBaseUrl() != null) {
      return URI.create(config.getBaseUrl()).resolve(baseUri.getPath());
    }
    return baseUri;
  }

  @NotNull
  public static ObjectMapper createJsonMapper() {
    final ObjectMapper mapper = new ObjectMapper();
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    return mapper;
  }

  @NotNull
  public static JacksonJsonProvider createJsonProvider() {
    JacksonJaxbJsonProvider provider = new JacksonJaxbJsonProvider();
    provider.setMapper(createJsonMapper());
    return provider;
  }

  @NotNull
  public static ResourceConfig createResourceConfig(@NotNull LocalContext localContext) {
    final ResourceConfig rc = new ResourceConfig();
    rc.register(WebServer.createJsonProvider());
    rc.register(new WebExceptionMapper());
    rc.register(new AuthenticationFilterReader(localContext));
    rc.register(new AuthenticationFilterWriter(localContext));
    rc.register(new AuthenticationFilterUnchecked(localContext));
    rc.register(new AbstractBinder() {
      @Override
      protected void configure() {
        bindFactory(UserInjectionFactory.class).to(User.class);
      }
    });
    return rc;
  }

  public static final class ServletInfo {
    @NotNull
    private final String path;
    @NotNull
    private final ServletHolder holder;
    @NotNull
    private final ServletMapping mapping;

    private ServletInfo(@NotNull String pathSpec, @NotNull Servlet servlet) {
      path = pathSpec;
      holder = new ServletHolder(servlet);
      mapping = new ServletMapping();
      mapping.setServletName(holder.getName());
      mapping.setPathSpec(pathSpec);
    }
  }
}
