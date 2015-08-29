/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.eclipse.jgit.util.Base64;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.context.Shared;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsWriter;
import svnserver.ext.web.server.WebServer;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LFS server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsServer implements Shared {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(LfsServer.class);
  @NotNull
  private static final Pattern OID_PATH_INFO = Pattern.compile("^/([0-9a-f]{64})$");
  @NotNull
  private static final String MIME_TYPE = "application/vnd.git-lfs+json";
  @NotNull
  private static final String AUTH_BASIC = "Basic ";
  @NotNull
  private static final String AUTH_TOKEN = "Token ";

  @Nullable
  private WebServer webServer;
  @NotNull
  private SharedContext context;
  @Nullable
  private String privateToken;

  public LfsServer(@Nullable String privateToken) {
    this.privateToken = privateToken;
  }

  @Override
  public void init(@NotNull SharedContext context) throws IOException, SVNException {
    this.webServer = WebServer.get(context);
    this.context = context;
  }

  private void sendError(@NotNull HttpServletResponse resp, int sc, @NotNull String message, @Nullable String documentationUrl) throws IOException {
    resp.setStatus(sc);
    resp.setContentType(MIME_TYPE);
    if (sc == HttpServletResponse.SC_UNAUTHORIZED) {
      resp.addHeader("WWW-Authenticate", "Basic realm=\"" + webServer.getRealm() + "\"");
    }
    JsonWriter writer = new JsonWriter(resp.getWriter());
    writer.setIndent("\t");
    writer.beginObject();
    writer.name("message").value(message);
    if (documentationUrl != null) {
      writer.name("documentation_url").value(documentationUrl);
    }
    writer.endObject();
  }

  @Nullable
  private static String getOid(@Nullable String pathInfo) {
    if (pathInfo == null) return null;
    final Matcher matcher = OID_PATH_INFO.matcher(pathInfo);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
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

  public void register(@NotNull String name, @NotNull LfsStorage storage) {
    if (webServer == null) throw new IllegalStateException("Object is non-initialized");
    webServer.addServlet("/" + name + ".git/info/lfs/objects/*", new LfsObjectsServlet(storage));
    webServer.addServlet("/" + name + ".git/info/lfs/storage/*", new LfsStorageServlet(storage));
  }

  public void unregister(@NotNull String name) {
    if (webServer == null) throw new IllegalStateException("Object is non-initialized");
    webServer.removeServlet("/" + name + ".git/info/lfs/storage/*");
    webServer.removeServlet("/" + name + ".git/info/lfs/objects/*");
  }

  private class LfsObjectsServlet extends HttpServlet {
    @NotNull
    private final LfsStorage storage;

    public LfsObjectsServlet(@NotNull LfsStorage storage) {
      this.storage = storage;
    }

    @Override
    protected void doPost(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp) throws ServletException, IOException {
      final User user = getAuthInfo(req);
      if (user == null) {
        sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", null);
        return;
      }
      if (req.getPathInfo() != null) {
        super.doPost(req, resp);
        return;
      }
      if (!checkMimeType(req.getContentType(), MIME_TYPE)) {
        sendError(resp, HttpServletResponse.SC_NOT_ACCEPTABLE, "Not Acceptable", null);
        return;
      }

      String oid = null;
      try (final BufferedReader reader = req.getReader()) {
        final JsonReader json = new JsonReader(reader);
        json.beginObject();
        while (json.hasNext()) {
          final String name = json.nextName();
          if ("oid".equals(name)) {
            oid = json.nextString();
          } else {
            json.skipValue();
          }
        }
        json.endObject();
      }
      if (oid == null) {
        sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "OID not defined", "https://github.com/github/git-lfs/blob/master/docs/api/http-v1-original.md");
        return;
      }
      resp.addHeader("Content-Type", MIME_TYPE);
      final LfsReader reader = storage.getReader(LfsStorage.OID_PREFIX + oid);
      if (reader != null) {
        // Already uploaded
        resp.setStatus(HttpServletResponse.SC_OK);
        JsonWriter writer = new JsonWriter(resp.getWriter());
        writer.setIndent("\t");
        writer.beginObject();
        writer.name("oid").value(reader.getOid(true));
        writer.name("size").value(reader.getSize());
        writer.name("_links").beginObject();
        writer.name("download").beginObject();
        writer.name("href").value(joinUrl(getUrl(req), "storage/" + reader.getOid(true)));
        writer.name("header").beginObject();
        writer.name(HttpHeaders.AUTHORIZATION).value(AUTH_TOKEN + createToken(user));
        writer.endObject();// header
        writer.endObject();// download
        writer.endObject();// _links
        writer.endObject();
        writer.close();
      } else {
        // Can be uploaded
        resp.setStatus(HttpServletResponse.SC_ACCEPTED);
        JsonWriter writer = new JsonWriter(resp.getWriter());
        writer.setIndent("\t");
        writer.beginObject();
        writer.name("_links").beginObject();
        writer.name("upload").beginObject();
        writer.name("href").value(joinUrl(getUrl(req), "storage/" + oid));
        writer.name("header").beginObject();
        writer.name(HttpHeaders.AUTHORIZATION).value(AUTH_TOKEN + createToken(user));
        writer.endObject();// header
        writer.endObject();// download
        writer.endObject();// _links
        writer.endObject();
        writer.close();
      }
    }

    @Override
    protected void doGet(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp) throws ServletException, IOException {
      final User user = getAuthInfo(req);
      if (user == null) {
        sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", null);
        return;
      }
      if (req.getPathInfo() == null) {
        // Write git-lfs-authenticate content.
        JsonWriter writer = new JsonWriter(resp.getWriter());
        writer.setIndent("\t");
        writer.beginObject();
        writer.name("header").beginObject();
        writer.name(HttpHeaders.AUTHORIZATION).value(AUTH_TOKEN + createToken(user));
        writer.endObject();// header
        writer.name("url").value(joinUrl(getUrl(req), "."));
        writer.endObject();
        writer.close();
        return;
      }
      if (!checkMimeType(req.getContentType(), MIME_TYPE)) {
        sendError(resp, HttpServletResponse.SC_NOT_ACCEPTABLE, "Not Acceptable", null);
        return;
      }
      final String oid = getOid(req.getPathInfo());
      if (oid == null) {
        sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Can't detect OID in URL", "https://github.com/github/git-lfs/blob/master/docs/api/http-v1-original.md");
        return;
      }
      final LfsReader reader = storage.getReader(LfsStorage.OID_PREFIX + oid);
      if (reader == null) {
        sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Object not found", null);
        return;
      }
      resp.addHeader("Content-Type", MIME_TYPE);
      JsonWriter writer = new JsonWriter(resp.getWriter());
      writer.setIndent("\t");
      writer.beginObject();
      writer.name("oid").value(reader.getOid(true));
      writer.name("size").value(reader.getSize());
      writer.name("_links").beginObject();
      writer.name("self").beginObject();
      writer.name("href").value(getUrl(req));
      writer.endObject();// self
      writer.name("download").beginObject();
      writer.name("href").value(joinUrl(getUrl(req), "../storage/" + reader.getOid(true)));
      writer.name("header").beginObject();
      writer.name(HttpHeaders.AUTHORIZATION).value(AUTH_TOKEN + createToken(user));
      writer.endObject();// header
      writer.endObject();// download
      writer.endObject();// _links
      writer.endObject();
      writer.close();
    }

    @NotNull
    private String joinUrl(String url, String path) {
      return URI.create(url).resolve(path).toString();
    }

    @NotNull
    private String getUrl(@NotNull HttpServletRequest req) {
      String host = req.getHeader(HttpHeaders.HOST);
      if (host == null) {
        host = req.getServerName() + ":" + req.getServerPort();
      }
      return req.getScheme() + "://" + host + req.getRequestURI();
    }
  }

  @Nullable
  private User getAuthInfo(@NotNull HttpServletRequest req) {
    final UserDB userDB = context.sure(UserDB.class);
    // Check privateToken authorization.
    final String token = req.getParameter("privateToken");
    if (privateToken != null && privateToken.equals(token)) {
      try {
        final String username = req.getParameter("username");
        if (username != null) {
          return userDB.lookupByUserName(username);
        }
        final String external = req.getParameter("external");
        if (external != null) {
          return userDB.lookupByExternal(external);
        }
      } catch (SVNException | IOException e) {
        log.error("Can't get user information", e);
        return null;
      }
    }
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
      return parseToken(authorization.substring(AUTH_TOKEN.length()));
    }
    return null;
  }

  @Nullable
  private User parseToken(@NotNull String token) {
    if (webServer == null) throw new IllegalStateException("Object is non-initialized");
    final UserDB userDB = context.sure(UserDB.class);
    try {
      final JsonWebEncryption jwe = webServer.createToken();
      jwe.setCompactSerialization(token);
      return userDB.lookupByUserName(jwe.getPayload());
    } catch (Exception e) {
      log.error("Token parsing error", e);
      return null;
    }
  }

  @NotNull
  private String createToken(@NotNull User user) {
    try {
      if (webServer == null) throw new IllegalStateException("Object is non-initialized");
      final JsonWebEncryption jwe = webServer.createToken();
      jwe.setPayload(user.getUserName());
      return jwe.getCompactSerialization();
    } catch (JoseException e) {
      throw new IllegalStateException(e);
    }
  }

  private class LfsStorageServlet extends HttpServlet {
    @NotNull
    private final LfsStorage storage;

    public LfsStorageServlet(@NotNull LfsStorage storage) {
      this.storage = storage;
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      final User user = getAuthInfo(req);
      if (user == null) {
        sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", null);
        return;
      }
      final String oid = getOid(req.getPathInfo());
      if (oid == null) {
        sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Can't detect OID in URL", "https://github.com/github/git-lfs/blob/master/docs/api/http-v1-original.md");
        return;
      }
      final LfsWriter writer = storage.getWriter(user);
      IOUtils.copy(req.getInputStream(), writer);
      writer.finish(LfsStorage.OID_PREFIX + oid);

      resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp) throws ServletException, IOException {
      final User user = getAuthInfo(req);
      if (user == null) {
        sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", null);
        return;
      }
      final String oid = getOid(req.getPathInfo());
      if (oid == null) {
        sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Can't detect OID in URL", "https://github.com/github/git-lfs/blob/master/docs/api/http-v1-original.md");
        return;
      }
      final LfsReader reader = storage.getReader(LfsStorage.OID_PREFIX + oid);
      if (reader == null) {
        sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Object not found", null);
        return;
      }
      resp.setContentType("application/octet-stream");
      sendContent(req, resp, reader);
    }

    private void sendContent(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp, @NotNull LfsReader reader) throws IOException {
      final ServletOutputStream output = resp.getOutputStream();
      if (acceptsGZipEncoding(req)) {
        try (InputStream stream = reader.openGzipStream()) {
          if (stream != null) {
            // Send already compressed stream
            resp.addHeader("Content-Encoding", "gzip");
            IOUtils.copy(stream, output);
            output.close();
            return;
          }
        }
      }
      // Send uncompressed stream
      resp.setContentLengthLong(reader.getSize());
      IOUtils.copy(reader.openStream(), output);
      output.close();
    }

    private boolean acceptsGZipEncoding(@NotNull HttpServletRequest req) {
      final String acceptEncoding = req.getHeader(HttpHeaders.ACCEPT_ENCODING);
      return acceptEncoding != null && acceptEncoding.contains("gzip");
    }
  }
}
