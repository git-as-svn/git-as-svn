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
import org.apache.http.HttpHeaders;
import org.jetbrains.annotations.NotNull;
import svnserver.auth.User;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * LFS storage pointer servlet.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsObjectsServlet extends LfsAbstractServlet {
  public LfsObjectsServlet(@NotNull SharedContext context, @NotNull LfsStorage storage) {
    super(context, storage);
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
    final LfsReader reader = getStorage().getReader(LfsStorage.OID_PREFIX + oid);
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
      writer.name(HttpHeaders.AUTHORIZATION).value(req.getHeader(HttpHeaders.AUTHORIZATION));
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
      writer.name(HttpHeaders.AUTHORIZATION).value(req.getHeader(HttpHeaders.AUTHORIZATION));
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
    if (!checkMimeType(req.getContentType(), MIME_TYPE)) {
      sendError(resp, HttpServletResponse.SC_NOT_ACCEPTABLE, "Not Acceptable", null);
      return;
    }
    final String oid = getOid(req.getPathInfo());
    if (oid == null) {
      sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Can't detect OID in URL", "https://github.com/github/git-lfs/blob/master/docs/api/http-v1-original.md");
      return;
    }
    final LfsReader reader = getStorage().getReader(LfsStorage.OID_PREFIX + oid);
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
    writer.name(HttpHeaders.AUTHORIZATION).value(req.getHeader(HttpHeaders.AUTHORIZATION));
    writer.endObject();// header
    writer.endObject();// download
    writer.endObject();// _links
    writer.endObject();
    writer.close();
  }
}
