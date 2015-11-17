/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.api.internal;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Service;
import org.atteo.classindex.ClassIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.ext.api.ProtobufFormat;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * Service information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ServiceInfo {
  @NotNull
  private static final ProtobufFormat[] formats = collectFormats();
  @NotNull
  private final Service service;
  @NotNull
  private final Map<String, MethodInfo> methods;
  @NotNull
  private final String name;

  public ServiceInfo(@NotNull Service service) {
    this.service = service;
    this.name = service.getDescriptorForType().getName().toLowerCase();

    final Map<String, MethodInfo> methods = new HashMap<>();
    for (Descriptors.MethodDescriptor method : service.getDescriptorForType().getMethods()) {
      for (ProtobufFormat format : formats) {
        final MethodInfo methodInfo = new MethodInfo(service, method, format);
        methods.put(method.getName().toLowerCase() + format.getSuffix(), methodInfo);
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

  private static ProtobufFormat[] collectFormats() {
    return StreamSupport
        .stream(ClassIndex.getSubclasses(ProtobufFormat.class).spliterator(), false)
        .filter(type -> !Modifier.isAbstract(type.getModifiers()))
        .map(type -> {
          try {
            return type.newInstance();
          } catch (IllegalAccessException | InstantiationException e) {
            throw new IllegalStateException(e);
          }
        })
        .toArray(ProtobufFormat[]::new);
  }

}
