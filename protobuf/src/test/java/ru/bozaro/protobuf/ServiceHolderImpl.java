/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf;

import com.google.protobuf.BlockingService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.protobuf.internal.ServiceInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple service holder.
 *
 * @author Artem V. Navrotskiy
 */
public class ServiceHolderImpl implements ServiceHolder {
  @NotNull
  private final Map<String, ServiceInfo> services = new HashMap<>();

  public ServiceHolderImpl(@NotNull BlockingService... services) {
    for (BlockingService service : services) {
      String name = service.getDescriptorForType().getName().toLowerCase();
      ServiceInfo info = new ServiceInfo(new BlockingServiceWrapper(service));
      this.services.put(name, info);
    }
  }

  @Override
  public @Nullable ServiceInfo getService(@NotNull String name) {
    return services.get(name);
  }
}
