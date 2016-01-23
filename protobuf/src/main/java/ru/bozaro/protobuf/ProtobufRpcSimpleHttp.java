/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf;

import com.google.protobuf.Message;
import org.apache.http.*;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.io.HttpMessageParser;
import org.apache.http.io.HttpMessageWriter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.bozaro.protobuf.internal.MethodInfo;
import ru.bozaro.protobuf.internal.ServiceInfo;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Servlet wrapper for Protobuf RPC
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ProtobufRpcSimpleHttp {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(ProtobufRpcSimpleHttp.class);
  @NotNull
  private final ServiceHolder holder;

  public ProtobufRpcSimpleHttp(@NotNull ServiceHolder holder) {
    this.holder = holder;
  }

  public void service(@NotNull HttpMessageParser<HttpRequest> parser, @NotNull HttpMessageWriter<HttpResponse> writer) throws IOException, HttpException {
    final HttpRequest request = parser.parse();
    final HttpResponse response = service(request);
    writer.write(response);
  }

  @NotNull
  protected HttpResponse service(@NotNull HttpRequest req) throws IOException {
    final String pathInfo = getPathInfo(req);
    if (pathInfo != null) {
      final int begin = pathInfo.charAt(0) == '/' ? 1 : 0;
      final int separator = pathInfo.indexOf('/', begin);
      if (separator > 0) {
        ServiceInfo serviceInfo = holder.getService(pathInfo.substring(begin, separator));
        if (serviceInfo != null) {
          return service(req, pathInfo.substring(separator + 1), serviceInfo);
        }
      }
    }
    return sendError(HttpStatus.SC_NOT_FOUND, "Service not found: " + pathInfo);
  }

  @NotNull
  public HttpResponse sendError(@NotNull int scNotFound, @NotNull String message) {
    return null;
  }

  private @NotNull HttpResponse service(@NotNull HttpRequest req, @NotNull String methodPath, @NotNull ServiceInfo serviceInfo) throws IOException {
    final MethodInfo method = serviceInfo.getMethod(methodPath);
    if (method == null) {
      return sendError(HttpStatus.SC_NOT_FOUND, "Method not found: " + methodPath);
    }
    final Message msgRequest;
    if (req instanceof HttpGet) {
      Message result;
      try {
        result = method.requestByParams(getParameterMap(req));
      } catch (ParseException e) {
        return sendError(HttpStatus.SC_BAD_REQUEST, e.getMessage());
      }
      msgRequest = result;
    } else if (req instanceof HttpPost) {
      final HttpPost post = (HttpPost) req;
      msgRequest = method.requestByStream(post.getEntity().getContent(), getCharset(post.getEntity()));
      if (msgRequest == null) {
        return sendError(HttpStatus.SC_BAD_REQUEST, "Method serialization reader is not supported.");
      }
    } else {
      return sendError(HttpStatus.SC_METHOD_NOT_ALLOWED, null);
    }
    try {
      final byte[] msgResponse = method.call(msgRequest, StandardCharsets.UTF_8).get();
      if (msgResponse != null) {
        final HttpResponse response = DefaultHttpResponseFactory.INSTANCE.newHttpResponse(req.getProtocolVersion(), HttpStatus.SC_OK, null);
        response.setEntity(new ByteArrayEntity(msgResponse, ContentType.create(method.getFormat().getMimeType(), StandardCharsets.UTF_8)));
        return response;
      } else {
        return sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR, null);
      }
    } catch (InterruptedException | ExecutionException e) {
      log.error("Method error " + method.getName(), e);
      return sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  @NotNull
  private String getPathInfo(@NotNull HttpRequest req) {
    return null;
  }

  @NotNull
  private Map<String, String[]> getParameterMap(@NotNull HttpRequest req) {
    return null;
  }

  @NotNull
  private static Charset getCharset(@NotNull HttpEntity entity) {
    final Header charset = entity.getContentEncoding();
    return charset == null ? StandardCharsets.UTF_8 : Charset.forName(charset.getValue());
  }
}
