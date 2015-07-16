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
import org.tmatesoft.svn.core.SVNException;
import svnserver.context.Shared;
import svnserver.context.SharedContext;

import javax.servlet.Servlet;
import java.io.IOException;

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

  public WebServer(@Nullable Server server) {
    this.server = server;
    if (server != null) {
      handler = new ServletHandler();
      server.setHandler(handler);
    } else {
      handler = null;
    }
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

  public static WebServer get(@NotNull SharedContext context) throws IOException, SVNException {
    return context.getOrCreate(WebServer.class, () -> new WebServer(null));
  }
}
