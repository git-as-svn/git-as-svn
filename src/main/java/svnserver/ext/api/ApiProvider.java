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
import ru.bozaro.protobuf.ProtobufRpcServlet;
import svnserver.api.core.Core;
import svnserver.context.Local;
import svnserver.context.LocalContext;
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
public class ApiProvider implements Shared, Local {
  @NotNull
  private final String path;
  @Nullable
  private WebServer.Holder servletInfo = null;

  public ApiProvider(@NotNull String path) {
    this.path = path;
  }

  public synchronized void init(@NotNull LocalContext context) throws IOException {
    if (servletInfo == null) {
      WebServer webServer = WebServer.get(context.getShared());
      final ServiceRegistry registry = ServiceRegistry.get(context);
      registry.addService(Core.newReflectiveBlockingService(new CoreRpc(registry)));

      final ProtobufRpcServlet servlet = new ProtobufRpcServlet(ServiceRegistry.get(context));
      servletInfo = webServer.addServlet("/" + path + "/*", servlet);
    }
  }

  @Override
  public synchronized void init(@NotNull SharedContext context) throws IOException {
    if (servletInfo == null) {
      WebServer webServer = WebServer.get(context);
      final ServiceRegistry registry = ServiceRegistry.get(context);
      registry.addService(Core.newReflectiveBlockingService(new CoreRpc(registry)));

      final ProtobufRpcServlet servlet = new ProtobufRpcServlet(registry);
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
