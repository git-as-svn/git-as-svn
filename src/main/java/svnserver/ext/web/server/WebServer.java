/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.web.server;

import org.eclipse.jetty.server.Server;
import org.jetbrains.annotations.NotNull;
import svnserver.context.Shared;
import svnserver.context.SharedContext;

import java.io.IOException;

/**
 * Web server component
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class WebServer implements Shared {
  private final Server server;

  public WebServer(@NotNull Server server) {
    this.server = server;
  }

  @Override
  public void ready(@NotNull SharedContext context) throws IOException {
    try {
      server.start();
    } catch (Exception e) {
      throw new IOException("Can't start http server", e);
    }
  }
}
