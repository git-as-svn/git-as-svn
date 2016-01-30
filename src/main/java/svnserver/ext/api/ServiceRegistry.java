/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.api;

import com.google.protobuf.BlockingService;
import com.google.protobuf.Service;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.protobuf.BlockingServiceWrapper;
import ru.bozaro.protobuf.ServiceHolder;
import ru.bozaro.protobuf.internal.ServiceInfo;
import svnserver.context.Local;
import svnserver.context.LocalContext;
import svnserver.context.Shared;
import svnserver.context.SharedContext;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service registry.  Used for add/remove services in runtime.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class ServiceRegistry implements ServiceHolder, Local, Shared {
  public final class Holder {

    @NotNull
    private final ServiceInfo service;

    private Holder(@NotNull ServiceInfo service) {
      this.service = service;
    }

    public void removeService() {
      ServiceRegistry.this.removeService(this);
    }

  }

  @NotNull
  private final ConcurrentHashMap<String, ServiceInfo> services = new ConcurrentHashMap<>();

  private ServiceRegistry() {
  }

  @Override
  @Nullable
  public ServiceInfo getService(@NotNull String name) {
    return services.get(name);
  }

  @NotNull
  public Set<String> getServices() {
    return new TreeSet<>(services.keySet());
  }

  @NotNull
  public Holder addService(@NotNull final BlockingService service) {
    return addService(new BlockingServiceWrapper(service));
  }

  @NotNull
  public Holder addService(@NotNull final Service service) {
    final ServiceInfo serviceInfo = new ServiceInfo(service);
    services.put(serviceInfo.getName(), serviceInfo);
    return new Holder(serviceInfo);
  }

  public boolean removeService(@NotNull final Holder holder) {
    return services.remove(holder.service.getName(), holder);
  }

  @Override
  public void close() {
  }

  @NotNull
  public static ServiceRegistry get(@NotNull SharedContext context) {
    return context.getOrCreate(ServiceRegistry.class, ServiceRegistry::new);
  }

  @NotNull
  public static ServiceRegistry get(@NotNull LocalContext context) {
    return context.getOrCreate(ServiceRegistry.class, ServiceRegistry::new);
  }
}
