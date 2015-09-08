/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.jetbrains.annotations.NotNull;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsWriter;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

/**
 * LFS storage servlet.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsStorageServlet extends LfsAbstractServlet {
  public LfsStorageServlet(@NotNull LocalContext context, @NotNull LfsStorage storage) {
    super(context, storage);
  }

  @Override
  protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    final User user = checkWriteAccess(req, resp);
    if (user == null) {
      return;
    }
    final String oid = getOid(req.getPathInfo());
    if (oid == null) {
      sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Can't detect OID in URL", "https://github.com/github/git-lfs/blob/master/docs/api/http-v1-original.md");
      return;
    }
    final LfsWriter writer = getStorage().getWriter(user);
    IOUtils.copy(req.getInputStream(), writer);
    writer.finish(LfsStorage.OID_PREFIX + oid);

    resp.setStatus(HttpServletResponse.SC_OK);
  }

  @Override
  protected void doGet(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp) throws ServletException, IOException {
    if (checkReadAccess(req, resp) == null) {
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
