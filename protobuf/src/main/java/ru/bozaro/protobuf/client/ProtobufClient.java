/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf.client;

import com.google.protobuf.*;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.protobuf.ProtobufFormat;
import ru.bozaro.protobuf.formatter.FormatBinary;

import java.net.URI;

/**
 * Client implementation for wrapping this RPC implementation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ProtobufClient implements BlockingRpcChannel {
  @NotNull
  private final URI baseUri;
  @NotNull
  private final HttpClient client;
  @NotNull
  private final ProtobufFormat format;

  public ProtobufClient(@NotNull URI baseUri, @Nullable HttpClient client, @Nullable ProtobufFormat format) {
    this.baseUri = baseUri;
    this.client = client != null ? client : HttpClients.createDefault();
    this.format = format != null ? format : new FormatBinary();
  }

  @Override
  public Message callBlockingMethod(@NotNull Descriptors.MethodDescriptor method, @Nullable RpcController controller, @NotNull Message request, @NotNull Message responsePrototype) throws ServiceException {

    return null;
  }
}
