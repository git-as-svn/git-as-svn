/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf.client;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.protobuf.ProtobufFormat;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Client implementation for wrapping this RPC implementation (test GET method).
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ProtobufClientGet extends ProtobufClient {
  public ProtobufClientGet(@NotNull URI baseUri, @Nullable HttpClient client, @Nullable ProtobufFormat format) {
    super(baseUri, client, format);
  }

  @NotNull
  public HttpUriRequest createRequest(@NotNull Descriptors.MethodDescriptor method, @NotNull Message request) throws IOException, URISyntaxException {
    final String serviceName = method.getService().getName().toLowerCase(Locale.ENGLISH);
    final String methodName = method.getName().toLowerCase(Locale.ENGLISH);
    final URIBuilder builder = new URIBuilder(getBaseUri().resolve(serviceName + "/" + methodName + getFormat().getSuffix()));
    for (Descriptors.FieldDescriptor field : method.getInputType().getFields()) {
      if (request.hasField(field)) {
        builder.addParameter(field.getName(), request.getField(field).toString());
      }
    }
    return new HttpGet(builder.build());
  }
}
