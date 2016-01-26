/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.socket.config;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.bozaro.protobuf.ProtobufRpcSocket;
import svnserver.context.Shared;
import svnserver.context.SharedContext;
import svnserver.ext.api.ServiceRegistry;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Unix socket transport for API.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SocketRpc implements Shared {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(SocketRpc.class);
  private static final long TIMEOUT = 10000;
  @NotNull
  private final ExecutorService poolExecutor;
  @NotNull
  private final ProtobufRpcSocket server;

  public SocketRpc(@NotNull SharedContext context, @NotNull ServerSocket serverSocket) throws IOException {
    this.poolExecutor = Executors.newCachedThreadPool();
    log.info("Server API on socket: {}", serverSocket);
    this.server = new ProtobufRpcSocket(ServiceRegistry.get(context), serverSocket, poolExecutor);
  }

  @Override
  public void close() throws Exception {
    poolExecutor.shutdown();
    server.close();
    poolExecutor.awaitTermination(TIMEOUT, TimeUnit.MILLISECONDS);
  }
}
