/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf.internal;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Service;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.protobuf.ProtobufFormat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Service information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ServiceInfo {
  @NotNull
  private final Map<String, MethodInfo> methods;
  @NotNull
  private final String name;

  public ServiceInfo(@NotNull Service service) {
    this.name = service.getDescriptorForType().getName().toLowerCase(Locale.ENGLISH);

    final Map<String, MethodInfo> methods = new HashMap<>();
    for (Descriptors.MethodDescriptor method : service.getDescriptorForType().getMethods()) {
      for (ProtobufFormat format : ProtobufFormat.getFormats()) {
        final MethodInfo methodInfo = new MethodInfo(service, method, format);
        methods.put(method.getName().toLowerCase(Locale.ENGLISH) + format.getSuffix(), methodInfo);
      }
    }
    this.methods = methods;
  }

  @NotNull
  public String getName() {
    return name;
  }

  @Nullable
  public MethodInfo getMethod(@NotNull String path) {
    return methods.get(path);
  }
}
