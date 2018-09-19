/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import org.eclipse.jgit.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.gitlfs.common.JsonHelper;
import ru.bozaro.gitlfs.common.data.Link;
import ru.bozaro.gitlfs.server.ServerError;
import svnserver.context.LocalContext;
import svnserver.ext.gitlfs.LfsAuthHelper;
import svnserver.ext.web.server.WebServer;

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
final class LfsAuthServlet extends HttpServlet {
  @NotNull
  private final LocalContext context;
  @NotNull
  private final String baseLfsUrl;
  @NotNull
  private final String secretToken;
  private final int tokenExpireSec;
  private final float tokenExpireTime;

  LfsAuthServlet(@NotNull LocalContext context, @NotNull String baseLfsUrl, @NotNull String secretToken, int tokenExpireSec, float tokenExpireTime) {
    this.context = context;
    this.baseLfsUrl = baseLfsUrl;
    this.secretToken = secretToken;
    this.tokenExpireSec = tokenExpireSec;
    this.tokenExpireTime = tokenExpireTime;
  }

  @Override
  protected void doGet(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp) throws IOException {
    createToken(req, resp);
  }

  @Override
  protected void doPost(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp) throws IOException {
    createToken(req, resp);
  }

  private void createToken(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp) throws IOException {
    try {
      final Link token = createToken(
          req,
          getUriParam(req, "url"),
          getStringParam(req, "secretToken"),
          getStringParam(req, "userId"),
          getStringParam(req, "mode")
      );
      resp.setContentType("application/json");
      JsonHelper.createMapper().writeValue(resp.getOutputStream(), token);
    } catch (ServerError e) {
      getWebServer().sendError(req, resp, e);
    }
  }

  private Link createToken(
      @NotNull HttpServletRequest req,
      @Nullable URI uri,
      @Nullable String secretToken,
      @Nullable String userId,
      @Nullable String mode
      ) throws ServerError {
    // Check secretToken
    if (StringUtils.isEmptyOrNull(secretToken))
      throw new ServerError(HttpServletResponse.SC_BAD_REQUEST, "Parameter \"secretToken\" not specified");

    if (!this.secretToken.equals(secretToken))
      throw new ServerError(HttpServletResponse.SC_FORBIDDEN, "Invalid secretToken");

    final LfsAuthHelper.AuthMode authMode = LfsAuthHelper.AuthMode.find(mode);
    if (authMode == null)
      throw new ServerError(HttpServletResponse.SC_BAD_REQUEST, "Invalid mode");

    if (userId == null)
      throw new ServerError(HttpServletResponse.SC_BAD_REQUEST, "Parameter \"mode\" not specified");

    return LfsAuthHelper.createToken(context.getShared(), uri != null ? uri : getWebServer().getUrl(req).resolve(baseLfsUrl), userId, authMode, tokenExpireSec, tokenExpireTime);
  }

  @Nullable
  private URI getUriParam(@NotNull HttpServletRequest req, @NotNull String name) {
    final String value = getStringParam(req, name);
    if (value == null) return null;
    return URI.create(value);
  }

  @Nullable
  private static String getStringParam(@NotNull HttpServletRequest req, @NotNull String name) {
    return req.getParameter(name);
  }

  private static boolean getBoolParam(@NotNull HttpServletRequest req, @NotNull String name, boolean defaultValue) {
    final String value = getStringParam(req, name);
    if (value == null) return defaultValue;
    return !(value.equals("0") || value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no"));
  }

  @NotNull
  private WebServer getWebServer() {
    return context.getShared().sure(WebServer.class);
  }
}
