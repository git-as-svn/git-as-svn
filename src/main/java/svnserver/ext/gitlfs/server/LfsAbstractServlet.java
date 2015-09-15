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
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.web.server.WebServer;
import svnserver.repository.VcsAccess;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base LFS servlet.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public abstract class LfsAbstractServlet extends HttpServlet {
  @NotNull
  public static final String MIME_TYPE = "application/vnd.git-lfs+json";
  @NotNull
  private static final Pattern OID_PATH_INFO = Pattern.compile("^/([0-9a-f]{64})$");

  @NotNull
  private final LocalContext context;
  @NotNull
  private final LfsStorage storage;

  @FunctionalInterface
  private interface Checker {
    void check(@NotNull VcsAccess access, @NotNull User user) throws SVNException, IOException;
  }

  public LfsAbstractServlet(@NotNull LocalContext context, @NotNull LfsStorage storage) {
    this.context = context;
    this.storage = storage;
  }

  @NotNull
  public LocalContext getContext() {
    return context;
  }

  @NotNull
  public SharedContext getShared() {
    return context.getShared();
  }

  @NotNull
  public WebServer getWebServer() {
    return context.getShared().sure(WebServer.class);
  }

  @NotNull

  public LfsStorage getStorage() {
    return storage;
  }

  @Nullable
  public User checkReadAccess(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp) throws IOException {
    return checkAccess(req, resp, (access, user) -> access.checkRead(user, null));
  }

  @Nullable
  public User checkWriteAccess(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp) throws IOException {
    return checkAccess(req, resp, (access, user) -> access.checkWrite(user, null));
  }

  @Nullable
  private User checkAccess(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp, @NotNull Checker checker) throws IOException {
    final VcsAccess access = context.sure(VcsAccess.class);
    User user = getWebServer().getAuthInfo(req.getHeader(HttpHeaders.AUTHORIZATION));
    if (user == null) {
      user = User.getAnonymous();
    }
    try {
      checker.check(access, user);
      return user;
    } catch (SVNException ignored) {
    }
    if (user.isAnonymous()) {
      sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", null);
    } else {
      sendError(resp, HttpServletResponse.SC_FORBIDDEN, "Access forbidden", null);
    }
    return null;
  }

  @Nullable
  public static String getOid(@Nullable String pathInfo) {
    if (pathInfo == null) return null;
    final Matcher matcher = OID_PATH_INFO.matcher(pathInfo);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  public void sendError(@NotNull HttpServletResponse resp, int sc, @NotNull String message, @Nullable String documentationUrl) throws IOException {
    resp.setStatus(sc);
    resp.setContentType(MIME_TYPE);
    if (sc == HttpServletResponse.SC_UNAUTHORIZED) {
      resp.addHeader("WWW-Authenticate", "Basic realm=\"" + getWebServer().getRealm() + "\"");
    }
    try (StringWriter writer = new StringWriter()) {
      JsonWriter json = new JsonWriter(writer);
      json.setIndent("\t");
      json.beginObject();
      json.name("message").value(message);
      if (documentationUrl != null) {
        json.name("documentation_url").value(documentationUrl);
      }
      json.endObject();
      json.flush();
      json.close();
      resp.getWriter().println(writer.toString());
    }
  }

  public static boolean checkMimeType(@Nullable String contentType, @NotNull String mimeType) {
    String actualType = contentType;
    if (actualType != null) {
      int separator = actualType.indexOf(';');
      if (separator >= 0) {
        while (separator > 1 && actualType.charAt(separator - 1) == ' ') {
          separator--;
        }
        actualType = actualType.substring(0, separator);
      }
    }
    return mimeType.equals(actualType);
  }

  @NotNull
  protected String createHref(@NotNull HttpServletRequest req, @NotNull String path) {
    return URI.create(getWebServer().getUrl(req)).resolve("/" + context.getName() + path).toString();
  }
}
