/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.api;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import ru.bozaro.gitlfs.common.JsonHelper;
import ru.bozaro.gitlfs.common.data.Link;
import svnserver.api.lfs.AuthenticateRequest;
import svnserver.api.lfs.AuthenticateResponse;
import svnserver.api.lfs.Error;
import svnserver.api.lfs.Lfs;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.context.LocalContext;
import svnserver.ext.gitlfs.LfsAuthHelper;
import svnserver.ext.web.server.WebServer;

import java.io.IOException;
import java.net.URI;

/**
 * LFS API implementation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsRpc implements Lfs.BlockingInterface {
  @NotNull
  private final URI baseLfsUrl;
  @NotNull
  private final LocalContext context;

  public LfsRpc(@NotNull URI baseLfsUrl, @NotNull LocalContext context) {
    this.baseLfsUrl = baseLfsUrl;
    this.context = context;
  }

  @NotNull
  @Override
  public AuthenticateResponse authenticate(RpcController controller, AuthenticateRequest request) throws ServiceException {
    if ((request.getMode() != AuthenticateRequest.Mode.ANONYMOUS) && (!request.hasIdentificator())) {
      return AuthenticateResponse.newBuilder()
          .setError(svnserver.api.lfs.Error.newBuilder()
                  .setKind(Error.Kind.BAD_REQUEST)
                  .setDescription("Identificator field is required for authentication mode: " + request.getMode())
          )
          .build();
    }
    try {
      final UserDB userDB = context.getShared().sure(UserDB.class);
      final User user;
      switch (request.getMode()) {
        case ANONYMOUS:
          user = User.getAnonymous();
          break;
        case EXTERNAL:
          user = userDB.lookupByExternal(request.getIdentificator());
          break;
        case USERNAME:
          user = userDB.lookupByUserName(request.getIdentificator());
          break;
        default:
          throw new IllegalStateException("Unknown mode type: " + request.getMode());
      }
      if (user == null) {
        return AuthenticateResponse.newBuilder()
            .setError(svnserver.api.lfs.Error.newBuilder()
                    .setKind(Error.Kind.USER_NOT_FOUND)
                    .setDescription("User not found")
            )
            .build();
      }
      final URI url = request.hasUrl() ? URI.create(request.getUrl()) : getWebServer().getUrl(baseLfsUrl);
      Link token = LfsAuthHelper.createToken(context.getShared(), url, user);
      return AuthenticateResponse.newBuilder()
          .setSuccess(AuthenticateResponse.Success.newBuilder()
                  .setJson(JsonHelper.createMapper().writeValueAsString(token))
          )
          .build();
    } catch (SVNException | IOException e) {
      return AuthenticateResponse.newBuilder()
          .setError(svnserver.api.lfs.Error.newBuilder()
                  .setKind(Error.Kind.UNKNOWN_ERROR)
                  .setDescription("Can't get user information. See server log for more details")
          )
          .build();
    }
  }

  @NotNull
  public WebServer getWebServer() {
    return context.getShared().sure(WebServer.class);
  }
}
