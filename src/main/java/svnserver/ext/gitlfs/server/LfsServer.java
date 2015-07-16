/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import com.google.gson.stream.JsonWriter;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.context.Shared;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.web.server.WebServer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;

/**
 * LFS server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsServer implements Shared {

  private boolean inited = false;

  public LfsServer(@NotNull SharedContext context) {

  }

  @Override
  public void init(@NotNull SharedContext context) throws IOException, SVNException {
    if (inited) return;

    final WebServer server = WebServer.get(context);
    server.addServlet("/lfs/objects/*", new HttpServlet() {
      @Override
      protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // todo: Check for "Accept: application/vnd.git-lfs+json"
        final LfsStorage storage = context.get(LfsStorage.class);
        if (storage != null) {
          final String pathInfo = req.getPathInfo();
          if (pathInfo != null && pathInfo.startsWith("/") && pathInfo.indexOf('/', 1) < 0) {
            final String oid = LfsStorage.OID_PREFIX + pathInfo.substring(1);
            final LfsReader reader = storage.getReader(oid);
            if (reader != null) {
              resp.addHeader("Content-Type", "application/vnd.git-lfs+json");
              JsonWriter writer = new JsonWriter(resp.getWriter());
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
          }
        }
        super.doGet(req, resp);
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
      protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final LfsStorage storage = context.get(LfsStorage.class);
        if (storage != null) {
          final String pathInfo = req.getPathInfo();
          if (pathInfo != null && pathInfo.startsWith("/") && pathInfo.indexOf('/', 1) < 0) {
            final String oid = LfsStorage.OID_PREFIX + pathInfo.substring(1);
            final LfsReader reader = storage.getReader(oid);
            if (reader != null) {
              resp.addHeader("Content-Type", "application/octet-stream");
              IOUtils.copy(reader.openStream(), resp.getOutputStream());
              resp.getOutputStream().close();
            }
          }
        }
        super.doGet(req, resp);
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
    inited = true;
  }
}
