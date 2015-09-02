/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import com.google.gson.stream.JsonWriter;
import org.apache.http.HttpHeaders;
import org.jetbrains.annotations.NotNull;
import org.jose4j.jwt.NumericDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import svnserver.DateHelper;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.web.server.WebServer;
import svnserver.ext.web.token.TokenHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;

/**
 * LFS storage pointer servlet.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsAuthServlet extends LfsAbstractServlet {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(LfsAuthServlet.class);
  @NotNull
  private final String privateToken;

  public LfsAuthServlet(@NotNull SharedContext context, @NotNull LfsStorage storage, @NotNull String privateToken) {
    super(context, storage);
    this.privateToken = privateToken;
  }

  @Override
  protected void doPost(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp) throws ServletException, IOException {
    doWork(req, resp);
  }

  @Override
  protected void doGet(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp) throws ServletException, IOException {
    doWork(req, resp);
  }

  private void doWork(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp) throws ServletException, IOException {
    final UserDB userDB = getShared().sure(UserDB.class);
    // Check privateToken authorization.
    final String token = req.getParameter("token");
    if (token == null) {
      sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Parameter \"token\" is not defined", null);
      return;
    }
    if (!privateToken.equals(token)) {
      sendError(resp, HttpServletResponse.SC_FORBIDDEN, "Invalid token", null);
      return;
    }
    final User user;
    try {
      final String username = req.getParameter("username");
      final String external = req.getParameter("external");
      if (external == null && username == null) {
        sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Parameter \"username\" or \"external\" is not defined", null);
        return;
      }
      user = username != null ? userDB.lookupByUserName(username) : userDB.lookupByExternal(external);
    } catch (SVNException | IOException e) {
      sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Can't get user information. See log for more details", null);
      log.error("Can't get user information", e);
      return;
    }
    if (user == null) {
      sendError(resp, HttpServletResponse.SC_NOT_FOUND, "User not found", null);
      return;
    }

    // Calculate expire time and token.
    NumericDate expireAt = NumericDate.now();
    expireAt.addSeconds(60);
    final String accessToken = TokenHelper.createToken(getWebServer().createEncryption(), user, expireAt);

    // Write git-lfs-authenticate content.
    try (StringWriter writer = new StringWriter()) {
      JsonWriter json = new JsonWriter(writer);
      json.setIndent("\t");
      json.beginObject();
      json.name("href").value(createHref(req));
      json.name("header").beginObject();
      json.name(HttpHeaders.AUTHORIZATION).value(WebServer.AUTH_TOKEN + accessToken);
      json.endObject();// header
      json.name("expires_at").value(DateHelper.toISO8601(expireAt.getValueInMillis()));
      json.endObject();
      json.close();
      resp.getWriter().println(writer.toString());
    }
  }

  @NotNull
  private String createHref(@NotNull HttpServletRequest req) {
    final URI uri = URI.create(getWebServer().getUrl(req)).resolve(".");
    final String href = uri.toString();
    if (uri.getPath() != null && uri.getPath().endsWith("/")) {
      return href.substring(0, href.length() - 1);
    }
    return href;
  }
}
