/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.api.rpc;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.VersionInfo;
import svnserver.api.core.Core;
import svnserver.api.core.VersionRequest;
import svnserver.api.core.VersionResponse;
import svnserver.ext.api.ServiceRegistry;

import java.util.function.Consumer;

/**
 * Core API implementation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class CoreRpc implements Core.BlockingInterface {
  @NotNull
  private final ServiceRegistry registry;

  public CoreRpc(@NotNull final ServiceRegistry registry) {
    this.registry = registry;
  }

  @Override
  public VersionResponse version(RpcController controller, VersionRequest request) throws ServiceException {
    VersionResponse.Builder builder = VersionResponse.newBuilder()
        .setVersion(VersionInfo.getVersion());
    setField(builder::setVersion, VersionInfo.getRevision());
    setField(builder::setTag, VersionInfo.getTag());
    registry.getServices().forEach(builder::addService);
    return builder.build();
  }

  private <T> void setField(@NotNull Consumer<T> setter, @Nullable T value) {
    if (value != null) setter.accept(value);
  }
}
