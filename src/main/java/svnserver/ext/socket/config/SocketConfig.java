/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.socket.config;

import org.jetbrains.annotations.NotNull;
import svnserver.config.ConfigHelper;
import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;

import java.io.IOException;

/**
 * Unix socket transport for API.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("socket")
public class SocketConfig implements SharedConfig {
  @NotNull
  private String path = "git-as-svn.socket";

  @Override
  public void create(@NotNull SharedContext context) throws IOException {
    context.add(SocketRpc.class, new SocketRpc(context, ConfigHelper.joinPath(context.getBasePath(), path)));
  }
}
