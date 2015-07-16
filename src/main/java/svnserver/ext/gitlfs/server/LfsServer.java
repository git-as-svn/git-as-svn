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
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
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
  private static Pattern OID_PATH_INFO = Pattern.compile("^\\/([0-9a-f]{64})$");

  private boolean inited = false;

  @Override
  public void init(@NotNull SharedContext context) throws IOException, SVNException {
    if (inited) return;

    final WebServer server = WebServer.get(context);
    server.addServlet("/lfs/objects/*", new HttpServlet() {
      @Override
      protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getPathInfo() != null) {
          // todo: Error
          return;
        }
        if (!"application/vnd.git-lfs+json".equals(req.getContentType())) {
          // todo: Error
          return;
        }

        final LfsStorage storage = context.get(LfsStorage.class);
        if (storage == null) {
          // todo: Error
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
          // todo: Error
          return;
        }
        resp.addHeader("Content-Type", "application/vnd.git-lfs+json");
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
        if (!"application/vnd.git-lfs+json".equals(req.getHeader("Accept"))) {
          // todo: Error
          return;
        }
        final LfsStorage storage = context.get(LfsStorage.class);
        if (storage == null) {
          // todo: Error
          return;
        }
        final String oid = getOid(req.getPathInfo());
        if (oid == null) {
          // todo: Error
          return;
        }
        final LfsReader reader = storage.getReader(LfsStorage.OID_PREFIX + oid);
        if (reader == null) {
          // todo: Error
          return;
        }
        resp.addHeader("Content-Type", "application/vnd.git-lfs+json");
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
          // todo: Error
          return;
        }
        final String oid = getOid(req.getPathInfo());
        if (oid == null) {
          // todo: Error
          return;
        }
        final LfsWriter writer = storage.getWriter();
        IOUtils.copy(req.getInputStream(), writer);
        writer.finish(LfsStorage.OID_PREFIX + oid);

        resp.setStatus(HttpServletResponse.SC_OK);
      }

      @Override
      protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final LfsStorage storage = context.get(LfsStorage.class);
        if (storage == null) {
          // todo: Error
          return;
        }
        final String oid = getOid(req.getPathInfo());
        if (oid == null) {
          // todo: Error
          return;
        }
        final LfsReader reader = storage.getReader(LfsStorage.OID_PREFIX + oid);
        if (reader == null) {
          // todo: Error
          return;
        }
        resp.setContentType("application/octet-stream");
        if (acceptsGZipEncoding(req)) {
          resp.addHeader("Content-Encoding", "gzip");
          IOUtils.copy(reader.openGzipStream(), resp.getOutputStream());
        } else {
          resp.setContentLengthLong(reader.getSize());
          IOUtils.copy(reader.openStream(), resp.getOutputStream());
        }
        resp.getOutputStream().close();
      }

      private boolean acceptsGZipEncoding(@NotNull HttpServletRequest req) {
        final String acceptEncoding = req.getHeader("Accept-Encoding");
        return acceptEncoding != null && acceptEncoding.contains("gzip");
      }
    });
    inited = true;
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
