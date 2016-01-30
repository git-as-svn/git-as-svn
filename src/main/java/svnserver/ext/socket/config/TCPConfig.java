/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.socket.config;

import org.jetbrains.annotations.NotNull;
import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * TCP socket transport for API.
 * <p>
 * This is developers-only API endpoint implementation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType(value = "tcp", unsafe = true)
public class TCPConfig implements SharedConfig {
  @NotNull
  private String host = "localhost";
  private int port = 8124;

  @Override
  public void create(@NotNull SharedContext context) throws IOException {
    final ServerSocket socket = new ServerSocket();
    socket.bind(new InetSocketAddress(host, port));

    context.add(SocketRpc.class, new SocketRpc(context, socket));
  }
}
