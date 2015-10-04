/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.httpclient.HttpStatus;
import org.jetbrains.annotations.NotNull;
import ru.bozaro.gitlfs.client.Constants;
import ru.bozaro.gitlfs.common.data.*;
import ru.bozaro.gitlfs.common.data.Error;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.web.annotations.SecureReader;
import svnserver.ext.web.annotations.SecureUnchecked;
import svnserver.ext.web.annotations.SecureWriter;
import svnserver.ext.web.server.AuthenticationFilter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.*;

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
  @SecureUnchecked
  @Path(value = "/batch")
  @Consumes(MIME_TYPE)
  @Produces(MIME_TYPE)
  public BatchRes postBatch(@NotNull BatchReq batch,
                            @Context User user,
                            @Context UriInfo ui,
                            @Context HttpServletRequest req,
                            @Context HttpServletResponse res
  ) throws IOException {
    AuthenticationFilter.checkAccess(getContext(), user, batch.getOperation().visit(new AccessVisitor()));
    return batch.getOperation().visit(new Operation.Visitor<PostBatchTask>() {
      @Override
      public PostBatchTask visitDownload() {
        return new PostBatchTask(false);
      }

      @Override
      public PostBatchTask visitUpload() {
        return new PostBatchTask(true);
      }
    }).postBatch(batch, user, ui, req, res);
  }

  @POST
  @SecureWriter
  @Path(value = "")
  @Consumes(MIME_TYPE)
  @Produces(MIME_TYPE)
  public ObjectRes postMeta(@NotNull Meta meta,
                            @Context UriInfo ui,
                            @Context HttpServletRequest req,
                            @Context HttpServletResponse res
  ) throws IOException {
    final String oid = meta.getOid();
    final LfsReader reader = getStorage().getReader(LfsStorage.OID_PREFIX + oid);
    if (reader != null) {
      final String id = reader.getOid(true);
      return new ObjectRes(
          id,
          reader.getSize(),
          ImmutableMap.<String, Link>builder()
              .put("download", new Link(
                  createHref(ui, pathStorage + id),
                  authHeader(req),
                  null
              ))
              .build()
      );
    } else {
      // Can be uploaded
      res.setStatus(HttpServletResponse.SC_ACCEPTED);
      res.flushBuffer();
      return new ObjectRes(
          null,
          0,
          ImmutableMap.<String, Link>builder()
              .put("upload", new Link(
                  createHref(ui, pathStorage + oid),
                  authHeader(req),
                  null
              ))
              .build()
      );
    }
  }

  @GET
  @SecureReader
  @Path(value = "/{oid:[0-9a-f]{64}}")
  @Produces(MIME_TYPE)
  public ObjectRes getMeta(@PathParam(value = "oid") String oid,
                           @Context UriInfo ui,
                           @Context HttpServletRequest req
  ) throws IOException {
    final LfsReader reader = getStorage().getReader(LfsStorage.OID_PREFIX + oid);
    if (reader == null) {
      throw new NotFoundException("Object not found: " + oid);
    }
    return new ObjectRes(
        reader.getOid(true),
        reader.getSize(),
        ImmutableMap.<String, Link>builder()
            .put("self", new Link(
                createHref(ui, pathObjects + oid),
                Collections.emptyMap(),
                null
            ))
            .put("download", new Link(
                createHref(ui, pathStorage + oid),
                authHeader(req),
                null
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

  private static class AccessVisitor implements Operation.Visitor<AuthenticationFilter.Checker> {
    @Override
    public AuthenticationFilter.Checker visitDownload() {
      return (access, user) -> access.checkRead(user, null);
    }

    @Override
    public AuthenticationFilter.Checker visitUpload() {
      return (access, user) -> access.checkWrite(user, null);
    }
  }

  private class PostBatchTask {
    private final boolean upload;

    public PostBatchTask(boolean upload) {
      this.upload = upload;
    }

    @NotNull
    public BatchRes postBatch(@NotNull BatchReq batch, @Context User user, @Context UriInfo ui, @Context HttpServletRequest req, @Context HttpServletResponse res) throws IOException {
      final List<BatchItem> items = new ArrayList<>();
      for (Meta meta : batch.getObjects()) {
        final LfsReader reader = getStorage().getReader(LfsStorage.OID_PREFIX + meta.getOid());
        final BatchItem item;
        if (reader != null) {
          item = new BatchItem(meta, ImmutableMap.<String, Link>builder()
              .put(Constants.LINK_DOWNLOAD, new Link(
                  createHref(ui, pathStorage + meta.getOid()),
                  authHeader(req),
                  null
              ))
              .build());
        } else if (upload) {
          item = new BatchItem(meta, ImmutableMap.<String, Link>builder()
              .put(Constants.LINK_UPLOAD, new Link(
                  createHref(ui, pathStorage + meta.getOid()),
                  authHeader(req),
                  null
              ))
              .build());
        } else {
          item = new BatchItem(meta, new Error(HttpStatus.SC_NOT_FOUND, "Object not found: " + meta.getOid()));
        }
        items.add(item);
      }
      return new BatchRes(items);
    }
  }
}
