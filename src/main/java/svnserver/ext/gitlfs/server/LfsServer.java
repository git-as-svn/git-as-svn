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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LFS server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsServer implements Shared {
  @NotNull
  private static Pattern OID_PATH_INFO = Pattern.compile("^/([0-9a-f]{64})$");
  @NotNull
  private static String MIME_TYPE = "application/vnd.git-lfs+json";

  private boolean inited = false;

  @Override
  public void init(@NotNull SharedContext context) throws IOException, SVNException {
    if (inited) return;

    final WebServer server = WebServer.get(context);
    server.addServlet("/lfs/objects/*", new HttpServlet() {
      @Override
      protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getPathInfo() != null) {
          super.doPost(req, resp);
          return;
        }
        /*if (!MIME_TYPE.equals(req.getContentType())) {
          sendError(resp, HttpServletResponse.SC_NOT_ACCEPTABLE, "Not Acceptable", null);
          return;
        }*/

        final LfsStorage storage = context.get(LfsStorage.class);
        if (storage == null) {
          sendError(resp, HttpServletResponse.SC_NOT_IMPLEMENTED, "LFS storage not found", null);
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
          writer.endObject();// header
          writer.endObject();// download
          writer.endObject();// _links
          writer.endObject();
          writer.close();
        }
      }

      @Override
      protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        /*if (!MIME_TYPE.equals(req.getHeader("Accept"))) {
          sendError(resp, HttpServletResponse.SC_NOT_ACCEPTABLE, "Not Acceptable", null);
          return;
        }*/
        final LfsStorage storage = context.get(LfsStorage.class);
        if (storage == null) {
          sendError(resp, HttpServletResponse.SC_NOT_IMPLEMENTED, "LFS storage not found", null);
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
        String host = req.getHeader("Host");
        if (host == null) {
          host = req.getServerName() + ":" + req.getServerPort();
        }
        return req.getScheme() + "://" + host + req.getRequestURI();
      }
    });
    server.addServlet("/lfs/storage/*", new HttpServlet() {
      @Override
      protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final LfsStorage storage = context.get(LfsStorage.class);
        if (storage == null) {
          sendError(resp, HttpServletResponse.SC_NOT_IMPLEMENTED, "LFS storage not found", null);
          return;
        }
        final String oid = getOid(req.getPathInfo());
        if (oid == null) {
          sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Can't detect OID in URL", "https://github.com/github/git-lfs/blob/master/docs/api/http-v1-original.md");
          return;
        }
        final LfsWriter writer = storage.getWriter();
        IOUtils.copy(req.getInputStream(), writer);
        writer.finish(LfsStorage.OID_PREFIX + oid);

        resp.setStatus(HttpServletResponse.SC_OK);
      }

      @Override
      protected void doGet(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp) throws ServletException, IOException {
        final LfsStorage storage = context.get(LfsStorage.class);
        if (storage == null) {
          sendError(resp, HttpServletResponse.SC_NOT_IMPLEMENTED, "LFS storage not found", null);
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
        final String acceptEncoding = req.getHeader("Accept-Encoding");
        return acceptEncoding != null && acceptEncoding.contains("gzip");
      }
    });
    inited = true;
  }

  private static void sendError(@NotNull HttpServletResponse resp, int sc, @NotNull String message, @Nullable String documentationUrl) throws IOException {
    resp.setStatus(sc);
    resp.setContentType(MIME_TYPE);
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
}
