/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import com.google.common.io.ByteStreams;
import org.jetbrains.annotations.NotNull;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsWriter;
import svnserver.ext.web.annotations.SecureReader;
import svnserver.ext.web.annotations.SecureWriter;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;

/**
 * LFS storage servlet.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@Path("/info/lfs/storage/")
public class LfsStorageResource extends LfsAbstractResource {
  public LfsStorageResource(@NotNull LocalContext context, @NotNull LfsStorage storage) {
    super(context, storage);
  }

  @PUT
  @SecureWriter
  @Path(value = "/{oid:[0-9a-f]{64}}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response putContent(@PathParam(value = "oid") String oid,
                             @Context User user,
                             InputStream stream
  ) throws IOException {
    final LfsWriter writer = getStorage().getWriter(user);
    ByteStreams.copy(stream, writer);
    writer.finish(LfsStorage.OID_PREFIX + oid);
    return Response.ok().build();
  }

  @GET
  @SecureReader
  @Path(value = "/{oid:[0-9a-f]{64}}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public void getContent(@PathParam(value = "oid") String oid,
                         @Context HttpServletRequest req,
                         @Context HttpServletResponse res
  ) throws IOException {
    final LfsReader reader = getStorage().getReader(LfsStorage.OID_PREFIX + oid);
    if (reader == null) {
      throw new NotFoundException();
    }
    res.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    sendContent(req, res, reader);
  }

  private void sendContent(@NotNull HttpServletRequest req, @NotNull HttpServletResponse resp, @NotNull LfsReader reader) throws IOException {
    final ServletOutputStream output = resp.getOutputStream();
    if (acceptsGZipEncoding(req)) {
      try (InputStream stream = reader.openGzipStream()) {
        if (stream != null) {
          // Send already compressed stream
          resp.addHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
          ByteStreams.copy(stream, output);
          output.close();
          return;
        }
      }
    }
    // Send uncompressed stream
    resp.setContentLengthLong(reader.getSize());
    ByteStreams.copy(reader.openStream(), output);
    output.close();
  }

  private boolean acceptsGZipEncoding(@NotNull HttpServletRequest req) {
    final String acceptEncoding = req.getHeader(HttpHeaders.ACCEPT_ENCODING);
    return acceptEncoding != null && acceptEncoding.contains("gzip");
  }
}
