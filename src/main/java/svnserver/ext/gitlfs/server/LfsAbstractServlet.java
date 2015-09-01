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
import svnserver.auth.User;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.web.server.WebServer;

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
  private final SharedContext context;
  @NotNull
  private final LfsStorage storage;

  public LfsAbstractServlet(@NotNull SharedContext context, @NotNull LfsStorage storage) {
    this.context = context;
    this.storage = storage;
  }

  @NotNull
  public SharedContext getShared() {
    return context;
  }

  @NotNull
  public WebServer getWebServer() {
    return context.sure(WebServer.class);
  }

  @NotNull

  public LfsStorage getStorage() {
    return storage;
  }

  @Nullable
  public User getAuthInfo(@NotNull HttpServletRequest req) {
    return getWebServer().getAuthInfo(req);
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
  public static String joinUrl(String url, String path) {
    return URI.create(url).resolve(path).toString();
  }

  @NotNull
  public static String getUrl(@NotNull HttpServletRequest req) {
    String host = req.getHeader(HttpHeaders.HOST);
    if (host == null) {
      host = req.getServerName() + ":" + req.getServerPort();
    }
    return req.getScheme() + "://" + host + req.getRequestURI();
  }
}
