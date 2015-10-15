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
import org.jetbrains.annotations.Nullable;
import ru.bozaro.gitlfs.client.Constants;
import ru.bozaro.gitlfs.common.data.Meta;
import ru.bozaro.gitlfs.common.data.Operation;
import ru.bozaro.gitlfs.server.ContentManager;
import ru.bozaro.gitlfs.server.ForbiddenError;
import ru.bozaro.gitlfs.server.UnauthorizedError;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsWriter;
import svnserver.ext.web.server.AuthenticationFilter;
import svnserver.ext.web.server.WebServer;

import javax.servlet.http.HttpServletRequest;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * ContentManager wrapper for shared LFS server implementation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsContentManager implements ContentManager<User> {
  @NotNull
  private final LocalContext context;
  @NotNull
  private final LfsStorage storage;

  public LfsContentManager(@NotNull LocalContext context, @NotNull LfsStorage storage) {
    this.context = context;
    this.storage = storage;
  }

  @Override
  public User checkAccess(@NotNull HttpServletRequest request, @NotNull Operation operation) throws IOException, UnauthorizedError, ForbiddenError {
    final WebServer server = context.getShared().sure(WebServer.class);
    final User user = server.getAuthInfo(request.getHeader(Constants.HEADER_AUTHORIZATION));
    AuthenticationFilter.checkAccess(context, user == null ? User.getAnonymous() : user, operation.visit(new AccessVisitor()));
    return user;
  }

  @Nullable
  @Override
  public Meta getMetadata(@NotNull String hash) throws IOException {
    final LfsReader reader = storage.getReader(LfsStorage.OID_PREFIX + hash);
    if (reader == null) {
      return null;
    }
    return new Meta(reader.getOid(true), reader.getSize());
  }

  @NotNull
  @Override
  public InputStream openObject(User context, @NotNull String hash) throws IOException {
    final LfsReader reader = storage.getReader(LfsStorage.OID_PREFIX + hash);
    if (reader == null) {
      throw new FileNotFoundException(hash);
    }
    return reader.openStream();
  }

  @Nullable
  @Override
  public InputStream openObjectGzipped(User context, @NotNull String hash) throws IOException {
    final LfsReader reader = storage.getReader(LfsStorage.OID_PREFIX + hash);
    if (reader == null) {
      throw new FileNotFoundException(hash);
    }
    return reader.openGzipStream();
  }

  @Override
  public void saveObject(User user, @NotNull Meta meta, @NotNull InputStream content) throws IOException {
    try (final LfsWriter writer = storage.getWriter(Objects.requireNonNull(user))) {
      ByteStreams.copy(content, writer);
      writer.finish(LfsStorage.OID_PREFIX + meta.getOid());
    }
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
}
