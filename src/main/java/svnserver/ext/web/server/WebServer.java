/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jose4j.jwe.JsonWebEncryption;
import org.tmatesoft.svn.core.SVNException;
import svnserver.context.Shared;
import svnserver.context.SharedContext;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * Web server component
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class WebServer implements Shared {
  @Nullable
  private final Server server;
  @Nullable
  private final ServletHandler handler;
  @NotNull
  private final String realm;
  @NotNull
  private final Supplier<JsonWebEncryption> tokenFactory;

  public WebServer(@Nullable Server server, @NotNull String realm, @NotNull Supplier<JsonWebEncryption> tokenFactory) {
    this.server = server;
    this.realm = realm;
    this.tokenFactory = tokenFactory;
    if (server != null) {
      handler = new ServletHandler();
      server.setHandler(handler);
    } else {
      handler = null;
    }
  }

  @NotNull
  public String getRealm() {
    return realm;
  }

  @NotNull
  public JsonWebEncryption createToken() {
    return tokenFactory.get();
  }

  @Override
  public void ready(@NotNull SharedContext context) throws IOException {
    try {
      if (server != null) {
        server.start();
      }
    } catch (Exception e) {
      throw new IOException("Can't start http server", e);
    }
  }

  public void addServlet(@NotNull String pathSpec, @NotNull Servlet servlet) {
    if (handler != null) {
      handler.addServletWithMapping(new ServletHolder(servlet), pathSpec);
    }
  }

  public void removeServlet(@NotNull String pathSpec) {
    // todo: Add remove servlet by pathSpec
  }

  public static WebServer get(@NotNull SharedContext context) throws IOException, SVNException {
    return context.getOrCreate(WebServer.class, () -> new WebServer(null, "Git as Subversion server", JsonWebEncryption::new));
  }
}
