/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.gitlfs.common.JsonHelper;
import ru.bozaro.gitlfs.common.data.Link;
import ru.bozaro.gitlfs.server.ServerError;
import svnserver.context.LocalContext;
import svnserver.ext.gitlfs.LfsAuthHelper;
import svnserver.ext.web.server.WebServer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;

/**
 * LFS storage pointer servlet.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsAuthServlet extends HttpServlet {
  @NotNull
  private final LocalContext context;
  @NotNull
  private final String baseLfsUrl;
  @Nullable
  private final String privateToken;
  private final int tokenExpireSec;
  private final float tokenExpireTime;

  public LfsAuthServlet(@NotNull LocalContext context, @NotNull String baseLfsUrl, @Nullable String privateToken, int tokenExpireSec, float tokenExpireTime) {
    this.context = context;
    this.baseLfsUrl = baseLfsUrl;
    this.privateToken = privateToken;
    this.tokenExpireSec = tokenExpireSec;
    this.tokenExpireTime = tokenExpireTime;
  }

  @Override
  protected void doGet(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp) throws ServletException, IOException {
    createToken(req, resp);
  }

  @Override
  protected void doPost(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp) throws ServletException, IOException {
    createToken(req, resp);
  }

  protected void createToken(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp) throws IOException {
    try {
      final Link token = createToken(
          req,
          getUriParam(req, "url"),
          getStringParam(req, "token"),
          getStringParam(req, "username"),
          getStringParam(req, "external"),
          getBoolParam(req, "anonymous", false)
      );
      resp.setContentType("application/json");
      JsonHelper.createMapper().writeValue(resp.getOutputStream(), token);
    } catch (ServerError e) {
      getWebServer().sendError(req, resp, e);
    }
  }

  public Link createToken(
      @NotNull HttpServletRequest req,
      @Nullable URI uri,
      @Nullable String token,
      @Nullable String username,
      @Nullable String external,
      boolean anonymous
  ) throws ServerError, IOException {
    if (privateToken == null) {
      throw new ServerError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Secret token is not defined in server configuration");
    }
    // Check privateToken authorization.
    if (token == null) {
      throw new ServerError(HttpServletResponse.SC_FORBIDDEN, "Parameter \"token\" is not defined");
    }
    if (!privateToken.equals(token)) {
      throw new ServerError(HttpServletResponse.SC_FORBIDDEN, "Invalid token");
    }
    return LfsAuthHelper.createToken(context.getShared(), uri != null ? uri : getWebServer().getUrl(req).resolve(baseLfsUrl), username, external, anonymous, tokenExpireSec, tokenExpireTime);
  }

  @NotNull
  public WebServer getWebServer() {
    return context.getShared().sure(WebServer.class);
  }

  @Nullable
  public static String getStringParam(@NotNull HttpServletRequest req, @NotNull String name) {
    return req.getParameter(name);
  }

  @Nullable
  private URI getUriParam(@NotNull HttpServletRequest req, @NotNull String name) {
    final String value = getStringParam(req, name);
    if (value == null) return null;
    return URI.create(value);
  }

  public static boolean getBoolParam(@NotNull HttpServletRequest req, @NotNull String name, boolean defaultValue) {
    final String value = getStringParam(req, name);
    if (value == null) return defaultValue;
    return !(value.equals("0") || value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no"));
  }
}
