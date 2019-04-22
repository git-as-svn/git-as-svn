/*
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
import svnserver.ext.gitlfs.config.LocalLfsConfig;
import svnserver.ext.web.server.WebServer;
import svnserver.ext.web.token.TokenHelper;

import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Helper for git-lfs-authenticate implementation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class LfsAuthHelper {
  @NotNull
  public static Link createToken(
      @NotNull SharedContext context,
      @NotNull URI baseLfsUrl,
      @Nullable String userId,
      @NotNull AuthMode authMode,
      int tokenExpireSec,
      float tokenEnsureTime
  ) throws ServerError {
    try {
      final UserDB userDB = context.sure(UserDB.class);
      final User user;
      if (authMode == AuthMode.Anonymous) {
        user = User.getAnonymous();
      } else if (userId == null) {
        throw new ServerError(HttpServletResponse.SC_BAD_REQUEST, "Parameter 'userId' not specified");
      } else {
        if (authMode == AuthMode.Username) {
          user = userDB.lookupByUserName(userId);
        } else {
          user = userDB.lookupByExternal(userId);
        }
      }
      if (user == null) {
        //throw new NotFoundException();
        throw new ServerError(HttpServletResponse.SC_NOT_FOUND, "User not found");
      }
      return createToken(context, baseLfsUrl, user, tokenExpireSec, tokenEnsureTime);
    } catch (SVNException e) {
      throw new ServerError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Can't get user information. See server log for more details", e);
    }
  }

  @NotNull
  private static Link createToken(
      @NotNull SharedContext context,
      @NotNull URI baseLfsUrl,
      @NotNull User user,
      int tokenExpireSec,
      float tokenEnsureTime
  ) {
    int expireSec = tokenExpireSec <= 0 ? LocalLfsConfig.DEFAULT_TOKEN_EXPIRE_SEC : tokenExpireSec;
    int ensureSec = (int) Math.ceil(expireSec * tokenEnsureTime);
    NumericDate now = NumericDate.now();
    NumericDate expireAt = NumericDate.fromSeconds(now.getValue() + expireSec);
    NumericDate ensureAt = NumericDate.fromSeconds(now.getValue() + ensureSec);
    return new Link(
        baseLfsUrl,
        createTokenHeader(context, user, expireAt),
        new Date(ensureAt.getValueInMillis())
    );
  }

  @NotNull
  public static Map<String, String> createTokenHeader(@NotNull SharedContext context,
                                                      @NotNull User user,
                                                      @NotNull NumericDate expireAt) {
    WebServer webServer = WebServer.get(context);
    final String accessToken = TokenHelper.createToken(webServer.createEncryption(), user, expireAt);
    return ImmutableMap.<String, String>builder()
        .put(Constants.HEADER_AUTHORIZATION, WebServer.AUTH_TOKEN + accessToken)
        .build();
  }

  @NotNull
  public static NumericDate getExpire(int tokenExpireSec) {
    // Calculate expire time and token.
    NumericDate expireAt = NumericDate.now();
    expireAt.addSeconds(tokenExpireSec <= 0 ? LocalLfsConfig.DEFAULT_TOKEN_EXPIRE_SEC : tokenExpireSec);
    return expireAt;
  }

  public enum AuthMode {
    Anonymous,
    Username,
    External;

    @Nullable
    public static AuthMode find(@Nullable String value) {
      for (AuthMode authMode : values())
        if (authMode.name().toLowerCase(Locale.ENGLISH).equals(value))
          return authMode;
      return null;
    }
  }
}
