/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.api.core.Core;
import svnserver.context.Shared;
import svnserver.context.SharedContext;
import svnserver.ext.api.rpc.CoreRpc;
import svnserver.ext.web.server.WebServer;

import java.io.IOException;

/**
 * API service provider.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ApiShared implements Shared {
  @NotNull
  private final String path;
  @Nullable
  private WebServer.Holder servletInfo = null;

  public ApiShared(@NotNull String path) {
    this.path = path;
  }

  @Override
  public synchronized void init(@NotNull SharedContext context) throws IOException, SVNException {
    if (servletInfo == null) {
      WebServer webServer = WebServer.get(context);
      final ProtobufRpcServlet servlet = new ProtobufRpcServlet();
      servlet.addService(Core.newReflectiveBlockingService(new CoreRpc()));
      servletInfo = webServer.addServlet(path + "/*", servlet);
    }
  }

  @Override
  public void ready(@NotNull SharedContext context) throws IOException {

  }

  @Override
  public synchronized void close() throws Exception {
    if (servletInfo != null) {
      servletInfo.removeServlet();
      servletInfo = null;
    }
  }
}
