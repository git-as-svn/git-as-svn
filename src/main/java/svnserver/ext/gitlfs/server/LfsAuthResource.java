/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jose4j.jwt.NumericDate;
import org.tmatesoft.svn.core.SVNException;
import ru.bozaro.gitlfs.common.data.Link;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.context.LocalContext;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.web.annotations.SecureWriter;
import svnserver.ext.web.server.WebServer;
import svnserver.ext.web.token.TokenHelper;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.URI;
import java.util.Date;

/**
 * LFS storage pointer servlet.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@Path("/auth/lfs")
public class LfsAuthResource extends LfsAbstractResource {
  @Nullable
  private final String privateToken;

  public LfsAuthResource(@NotNull LocalContext context, @NotNull LfsStorage storage, @Nullable String privateToken) {
    super(context, storage);
    this.privateToken = privateToken;
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  public Link createTokenPost(
      @Context UriInfo ui,
      @QueryParam("url") @FormParam("url") URI uri,
      @QueryParam("token") @FormParam("token") String token,
      @QueryParam("username") @FormParam("username") String username,
      @QueryParam("external") @FormParam("external") String external,
      @QueryParam("anonymous") @FormParam("anonymous") boolean anonymous
  ) {
    return createToken(ui, uri, token, username, external, anonymous);
  }

  @GET
  @Path("/test")
  @SecureWriter
  public String test() {
    return "Hello";
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Link createToken(
      @Context UriInfo ui,
      @QueryParam("url") URI uri,
      @QueryParam("token") String token,
      @QueryParam("username") String username,
      @QueryParam("external") String external,
      @QueryParam("anonymous") boolean anonymous
  ) {
    if (privateToken == null) {
      throw new NotSupportedException("Secret token is not defined in server configuration");
    }
    // Check privateToken authorization.
    if (token == null) {
      throw new BadRequestException("Parameter \"token\" is not defined");
    }
    if (!privateToken.equals(token)) {
      throw new ForbiddenException("Invalid token");
    }
    final UserDB userDB = getShared().sure(UserDB.class);
    final User user;
    try {
      if (anonymous) {
        user = User.getAnonymous();
      } else if (external == null && username == null) {
        throw new BadRequestException("Parameter \"username\" or \"external\" is not defined");
      } else {
        user = username != null ? userDB.lookupByUserName(username) : userDB.lookupByExternal(external);
      }
    } catch (SVNException | IOException e) {
      throw new InternalServerErrorException("Can't get user information. See log for more details", e);
    }
    if (user == null) {
      //throw new NotFoundException();
      throw new NotFoundException("User not found");
    }

    // Calculate expire time and token.
    NumericDate expireAt = NumericDate.now();
    expireAt.addSeconds(60);
    final String accessToken = TokenHelper.createToken(getWebServer().createEncryption(), user, expireAt);

    return new Link(
        uri != null ? uri : createHref(ui, LfsServer.SERVLET_BASE),
        ImmutableMap.<String, String>builder()
            .put(HttpHeaders.AUTHORIZATION, WebServer.AUTH_TOKEN + accessToken)
            .build(),
        new Date(expireAt.getValueInMillis())
    );
  }
}
