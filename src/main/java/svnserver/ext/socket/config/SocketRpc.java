/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.socket.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import ru.bozaro.protobuf.ProtobufRpcSocket;
import ru.bozaro.protobuf.internal.ServiceInfo;
import svnserver.context.Shared;
import svnserver.context.SharedContext;
import svnserver.ext.api.ServiceRegistry;
import svnserver.repository.RepositoryInfo;
import svnserver.repository.VcsRepositoryMapping;

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
final class SocketRpc implements Shared {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(SocketRpc.class);
  private static final long TIMEOUT = 10000;
  @NotNull
  private final ExecutorService poolExecutor;
  @NotNull
  private final ProtobufRpcSocket server;
  @NotNull
  private final SharedContext context;

  SocketRpc(@NotNull SharedContext context, @NotNull ServerSocket serverSocket) {
    this.poolExecutor = Executors.newCachedThreadPool();
    this.context = context;
    this.server = new ProtobufRpcSocket(SocketRpc.this::getService, serverSocket, poolExecutor);
  }

  @Override
  public void ready(@NotNull SharedContext context) throws IOException {
    this.server.start();
    log.info("Started {}", this.server);
  }

  @Nullable
  private ServiceInfo getService(@NotNull String name) {
    int separator = name.lastIndexOf('/');
    ServiceRegistry registry;
    if (separator < 0) {
      registry = ServiceRegistry.get(context);
    } else {
      try {
        VcsRepositoryMapping mapping = context.sure(VcsRepositoryMapping.class);
        SVNURL url = SVNURL.create("svn", null, "localhost", 0, name.substring(0, separator), false);
        RepositoryInfo repository = mapping.getRepository(url);
        if (repository == null || !repository.getBaseUrl().getPath().equals(url.getPath()))
          return null;
        registry = ServiceRegistry.get(repository.getRepository().getContext());
      } catch (SVNException e) {
        log.warn("Can't find repository", e);
        return null;
      }
    }
    return registry.getService(name.substring(separator + 1));
  }

  @Override
  public void close() throws Exception {
    poolExecutor.shutdown();
    server.close();
    poolExecutor.awaitTermination(TIMEOUT, TimeUnit.MILLISECONDS);
  }
}
