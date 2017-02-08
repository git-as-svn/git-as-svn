/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import ru.bozaro.gitlfs.common.Constants;
import ru.bozaro.gitlfs.common.data.Meta;
import ru.bozaro.gitlfs.server.ContentManager;
import ru.bozaro.gitlfs.server.ForbiddenError;
import ru.bozaro.gitlfs.server.UnauthorizedError;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.ext.gitlfs.LfsAuthHelper;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsWriter;
import svnserver.ext.web.server.WebServer;
import svnserver.repository.VcsAccess;

import javax.servlet.http.HttpServletRequest;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * ContentManager wrapper for shared LFS server implementation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsContentManager implements ContentManager {
  private final int tokenExpireSec;

  @FunctionalInterface
  public interface Checker {
    void check(@NotNull User user, @Nullable String path) throws SVNException, IOException;
  }

  @NotNull
  private final LocalContext context;
  @NotNull
  private final LfsStorage storage;

  public LfsContentManager(@NotNull LocalContext context, @NotNull LfsStorage storage, int tokenExpireSec) {
    this.context = context;
    this.storage = storage;
    this.tokenExpireSec = tokenExpireSec;
  }

  private User getAuthInfo(@NotNull HttpServletRequest request) {
    final WebServer server = context.getShared().sure(WebServer.class);
    final User user = server.getAuthInfo(request.getHeader(Constants.HEADER_AUTHORIZATION));
    return user == null ? User.getAnonymous() : user;
  }

  public int getTokenExpireSec() {
    return tokenExpireSec;
  }

  @NotNull
  Map<String, String> createHeader(@NotNull HttpServletRequest request, @NotNull User user) throws IOException {
    final String auth = request.getHeader(Constants.HEADER_AUTHORIZATION);
    if (auth == null) {
      return Collections.emptyMap();
    }
    if (auth.startsWith(WebServer.AUTH_TOKEN)) {
      return ImmutableMap.<String, String>builder()
          .put(Constants.HEADER_AUTHORIZATION, auth)
          .build();
    } else {
      return LfsAuthHelper.createTokenHeader(context.getShared(), user, LfsAuthHelper.getExpire(tokenExpireSec));
    }
  }

  @NotNull
  @Override
  public Downloader checkDownloadAccess(@NotNull HttpServletRequest request) throws IOException, ForbiddenError, UnauthorizedError {
    final VcsAccess access = context.sure(VcsAccess.class);
    final User user = checkAccess(request, access::checkRead);
    final Map<String, String> header = createHeader(request, user);
    return new Downloader() {
      @NotNull
      @Override
      public InputStream openObject(@NotNull String hash) throws IOException {
        final LfsReader reader = storage.getReader(LfsStorage.OID_PREFIX + hash);
        if (reader == null) {
          throw new FileNotFoundException(hash);
        }
        return reader.openStream();
      }

      @Nullable
      @Override
      public InputStream openObjectGzipped(@NotNull String hash) throws IOException {
        final LfsReader reader = storage.getReader(LfsStorage.OID_PREFIX + hash);
        if (reader == null) {
          throw new FileNotFoundException(hash);
        }
        return reader.openGzipStream();
      }

      @NotNull
      @Override
      public Map<String, String> createHeader(@NotNull Map<String, String> defaultHeader) {
        return header;
      }
    };
  }

  @NotNull
  @Override
  public Uploader checkUploadAccess(@NotNull HttpServletRequest request) throws IOException, ForbiddenError, UnauthorizedError {
    final VcsAccess access = context.sure(VcsAccess.class);
    final User user = checkAccess(request, access::checkWrite);
    final Map<String, String> header = createHeader(request, user);
    return new Uploader() {
      @Override
      public void saveObject(@NotNull Meta meta, @NotNull InputStream content) throws IOException {
        try (final LfsWriter writer = storage.getWriter(Objects.requireNonNull(user))) {
          ByteStreams.copy(content, writer);
          writer.finish(LfsStorage.OID_PREFIX + meta.getOid());
        }
      }

      @NotNull
      @Override
      public Map<String, String> createHeader(@NotNull Map<String, String> defaultHeader) {
        return header;
      }
    };
  }

  public User checkAccess(@NotNull HttpServletRequest request, @NotNull Checker checker) throws IOException, UnauthorizedError, ForbiddenError {
    final User user = getAuthInfo(request);
    try {
      checker.check(user, null);
    } catch (SVNException ignored) {
      if (user.isAnonymous()) {
        final WebServer server1 = context.getShared().sure(WebServer.class);
        throw new UnauthorizedError("Basic realm=\"" + server1.getRealm() + "\"");
      } else {
        throw new ForbiddenError();
      }
    }
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
}
