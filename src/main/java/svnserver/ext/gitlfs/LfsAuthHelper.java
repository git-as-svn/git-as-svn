/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jose4j.jwt.NumericDate;
import org.tmatesoft.svn.core.SVNException;
import ru.bozaro.gitlfs.common.Constants;
import ru.bozaro.gitlfs.common.data.Link;
import ru.bozaro.gitlfs.server.ServerError;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.context.SharedContext;
import svnserver.ext.web.server.WebServer;
import svnserver.ext.web.token.TokenHelper;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Date;

/**
 * Helper for git-lfs-authenticate implementation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsAuthHelper {
  public static Link createToken(
      @NotNull SharedContext context,
      @NotNull URI baseLfsUrl,
      @Nullable String username,
      @Nullable String external,
      boolean anonymous
  ) throws ServerError {
    try {
      final UserDB userDB = context.sure(UserDB.class);
      final User user;
      if (anonymous) {
        user = User.getAnonymous();
      } else if (external == null && username == null) {
        throw new ServerError(HttpServletResponse.SC_BAD_REQUEST, "Parameter \"username\" or \"external\" is not defined");
      } else {
        user = username != null ? userDB.lookupByUserName(username) : userDB.lookupByExternal(external);
      }
      if (user == null) {
        //throw new NotFoundException();
        throw new ServerError(HttpServletResponse.SC_NOT_FOUND, "User not found");
      }
      return createToken(context, baseLfsUrl, user);
    } catch (SVNException | IOException e) {
      throw new ServerError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Can't get user information. See server log for more details", e);
    }
  }

  public static Link createToken(
      @NotNull SharedContext context,
      @NotNull URI baseLfsUrl,
      @NotNull User user
  ) throws IOException {
    // Calculate expire time and token.
    NumericDate expireAt = NumericDate.now();
    expireAt.addSeconds(60);
    WebServer webServer = WebServer.get(context);
    final String accessToken = TokenHelper.createToken(webServer.createEncryption(), user, expireAt);

    return new Link(
        baseLfsUrl,
        ImmutableMap.<String, String>builder()
            .put(Constants.HEADER_AUTHORIZATION, WebServer.AUTH_TOKEN + accessToken)
            .build(),
        new Date(expireAt.getValueInMillis())
    );
  }
}
