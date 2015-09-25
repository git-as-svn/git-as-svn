/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.repository.VcsAccess;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;

/**
 * Base authenticator.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public abstract class AuthenticationFilter implements ContainerRequestFilter {
  @FunctionalInterface
  public interface Checker {
    void check(@NotNull VcsAccess access, @NotNull User user) throws SVNException, IOException;
  }

  @NotNull
  private final LocalContext context;
  @NotNull
  private final Checker checker;

  public AuthenticationFilter(@NotNull LocalContext context, @NotNull Checker checker) {
    this.context = context;
    this.checker = checker;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    final WebServer server = context.getShared().sure(WebServer.class);
    User user = server.getAuthInfo(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION));
    try {
      if (user == null) {
        user = User.getAnonymous();
      }
      checkAccess(context, user, checker);
      requestContext.setProperty(User.class.getName(), user);
    } catch (ClientErrorException e) {
      requestContext.abortWith(e.getResponse());
    }
  }

  @Nullable
  public static Response checkAccess(@NotNull LocalContext context, @NotNull User user, @NotNull Checker checker) throws IOException, ClientErrorException {
    final VcsAccess access = context.sure(VcsAccess.class);
    try {
      checker.check(access, user);
      return null;
    } catch (SVNException ignored) {
      if (user.isAnonymous()) {
        final WebServer server = context.getShared().sure(WebServer.class);
        throw new NotAuthorizedException(Response.status(Response.Status.UNAUTHORIZED)
            .header("WWW-Authenticate", "Basic realm=\"" + server.getRealm() + "\"")
            .entity("Unauthorized")
            .build());
      } else {
        throw new ForbiddenException(Response.status(Response.Status.FORBIDDEN)
            .entity("Access forbidden")
            .build());
      }
    }
  }
}
