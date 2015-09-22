/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import jersey.repackaged.com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import ru.bozaro.gitlfs.common.data.Link;
import ru.bozaro.gitlfs.common.data.Meta;
import svnserver.context.LocalContext;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.web.annotations.SecureReader;
import svnserver.ext.web.annotations.SecureWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * LFS storage pointer servlet.
 * <p>
 * https://github.com/github/git-lfs/blob/master/docs/api/http-v1-original.md
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@Path("/info/lfs/objects/")
public class LfsObjectsResource extends LfsAbstractResource {
  @NotNull
  private final String pathStorage;
  @NotNull
  private final String pathObjects;

  public LfsObjectsResource(@NotNull LocalContext context, @NotNull LfsStorage storage) {
    super(context, storage);
    pathStorage = "./" + LfsStorageResource.class.getAnnotation(Path.class).value();
    pathObjects = "./" + LfsObjectsResource.class.getAnnotation(Path.class).value();
  }

  @POST
  @SecureWriter
  @Path(value = "")
  @Consumes(MIME_TYPE)
  @Produces(MIME_TYPE)
  public Meta postMeta(Meta meta,
                       @Context UriInfo ui,
                       @Context HttpServletRequest req,
                       @Context HttpServletResponse res
  ) throws IOException {
    final String oid = meta.getOid();
    if (oid == null) {
      throw new BadRequestException();
    }

    final LfsReader reader = getStorage().getReader(LfsStorage.OID_PREFIX + oid);
    if (reader != null) {
      final String id = reader.getOid(true);
      return new Meta(
          id,
          reader.getSize(),
          ImmutableMap.<String, Link>builder()
              .put("download", new Link(
                  createHref(ui, pathStorage + id),
                  authHeader(req)
              ))
              .build()
      );
    } else {
      // Can be uploaded
      res.setStatus(HttpServletResponse.SC_ACCEPTED);
      res.flushBuffer();
      return new Meta(
          null,
          null,
          ImmutableMap.<String, Link>builder()
              .put("upload", new Link(
                  createHref(ui, pathStorage + oid),
                  authHeader(req)
              ))
              .build()
      );
    }
  }

  @GET
  @SecureReader
  @Path(value = "/{oid:[0-9a-f]{64}}")
  @Produces(MIME_TYPE)
  public Meta getMeta(@PathParam(value = "oid") String oid,
                      @Context UriInfo ui,
                      @Context HttpServletRequest req
  ) throws IOException {
    final LfsReader reader = getStorage().getReader(LfsStorage.OID_PREFIX + oid);
    if (reader == null) {
      throw new NotFoundException("Object not found: " + oid);
    }
    return new Meta(
        reader.getOid(true),
        reader.getSize(),
        ImmutableMap.<String, Link>builder()
            .put("self", new Link(
                createHref(ui, pathObjects + oid),
                null
            ))
            .put("download", new Link(
                createHref(ui, pathStorage + oid),
                authHeader(req)
            ))
            .build()
    );
  }

  @NotNull
  protected Map<String, String> authHeader(HttpServletRequest req) {
    final String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
    final Map<String, String> result = new TreeMap<>();
    if (auth != null) {
      result.put(HttpHeaders.AUTHORIZATION, auth);
    }
    return result;
  }
}
