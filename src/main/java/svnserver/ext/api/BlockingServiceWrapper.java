/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.api;

import com.google.protobuf.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Blocking service wrapper.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class BlockingServiceWrapper implements Service {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(BlockingServiceWrapper.class);
  @NotNull
  private final BlockingService service;

  public BlockingServiceWrapper(@NotNull BlockingService service) {
    this.service = service;
  }

  @NotNull
  @Override
  public Descriptors.ServiceDescriptor getDescriptorForType() {
    return service.getDescriptorForType();
  }

  @Override
  public void callMethod(@NotNull Descriptors.MethodDescriptor method, @NotNull RpcController controller, @NotNull Message request, @NotNull RpcCallback<Message> done) {
    try {
      done.run(service.callBlockingMethod(method, controller, request));
    } catch (ServiceException e) {
      log.error("Blocking method error " + method.getFullName(), e);
      done.run(null);
    }
  }

  @NotNull
  @Override
  public Message getRequestPrototype(@NotNull Descriptors.MethodDescriptor method) {
    return service.getRequestPrototype(method);
  }

  @NotNull
  @Override
  public Message getResponsePrototype(@NotNull Descriptors.MethodDescriptor method) {
    return service.getResponsePrototype(method);
  }
}
