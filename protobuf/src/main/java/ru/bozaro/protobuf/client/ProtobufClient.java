/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf.client;

import com.google.protobuf.*;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.protobuf.ProtobufFormat;
import ru.bozaro.protobuf.formatter.FormatBinary;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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
    this.baseUri = prepareUrl(baseUri);
    this.client = client != null ? client : HttpClients.createDefault();
    this.format = format != null ? format : new FormatBinary();
  }

  @NotNull
  public URI getBaseUri() {
    return baseUri;
  }

  @NotNull
  public ProtobufFormat getFormat() {
    return format;
  }

  @NotNull
  public static URI prepareUrl(@NotNull URI url) {
    final String path = url.getPath();
    return path == null || path.endsWith("/") ? url : url.resolve(path + "/");
  }

  @Override
  public Message callBlockingMethod(@NotNull Descriptors.MethodDescriptor method, @Nullable RpcController controller, @NotNull Message request, @NotNull Message responsePrototype) throws ServiceException {
    try {
      final HttpUriRequest httpRequest = createRequest(method, request);
      final HttpResponse response = client.execute(httpRequest);
      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
        try (final InputStream stream = response.getEntity().getContent()) {
          final Header encoding = response.getFirstHeader(HttpHeaders.CONTENT_ENCODING);
          final Charset charset;
          if (encoding != null && Charset.isSupported(encoding.getValue())) {
            charset = Charset.forName(encoding.getValue());
          } else {
            charset = StandardCharsets.UTF_8;
          }
          return format.read(responsePrototype.toBuilder(), stream, charset);
        }
      }
      return null;
    } catch (Exception e) {
      throw new ServiceException(e);
    }
  }

  @NotNull
  public HttpUriRequest createRequest(@NotNull Descriptors.MethodDescriptor method, @NotNull Message request) throws IOException, URISyntaxException {
    final HttpPost post = new HttpPost(baseUri.resolve(method.getService().getName().toLowerCase() + "/" + method.getName().toLowerCase() + format.getSuffix()));
    post.setHeader(HttpHeaders.CONTENT_TYPE, format.getMimeType());
    post.setHeader(HttpHeaders.CONTENT_ENCODING, StandardCharsets.UTF_8.name());
    final ByteArrayOutputStream stream = new ByteArrayOutputStream();
    format.write(request, stream, StandardCharsets.UTF_8);
    post.setEntity(new ByteArrayEntity(stream.toByteArray()));
    return post;
  }
}
