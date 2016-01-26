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
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.entity.EntityDeserializer;
import org.apache.http.impl.entity.EntitySerializer;
import org.apache.http.impl.entity.LaxContentLengthStrategy;
import org.apache.http.io.HttpMessageParser;
import org.apache.http.io.HttpMessageWriter;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.io.SessionOutputBuffer;
import org.apache.http.message.BasicHttpResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.bozaro.protobuf.internal.MethodInfo;
import ru.bozaro.protobuf.internal.ServiceInfo;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

  @SuppressWarnings("deprecation")
  protected void service(@NotNull SessionInputBuffer inputBuffer, @NotNull SessionOutputBuffer outputBuffer, @NotNull HttpMessageParser<HttpRequest> parser, @NotNull HttpMessageWriter<HttpResponse> writer) throws IOException, HttpException {
    try {
      final HttpRequest request = parser.parse();
      final HttpEntity entity;
      if (request instanceof HttpEntityEnclosingRequest) {
        final EntityDeserializer deserializer = new EntityDeserializer(new LaxContentLengthStrategy());
        entity = deserializer.deserialize(inputBuffer, request);
        ((HttpEntityEnclosingRequest) request).setEntity(entity);
      } else {
        entity = null;
      }
      final HttpResponse response = service(request);
      response.setHeader(HttpHeaders.SERVER, "Protobuf RPC");
      if (entity != null) {
        entity.getContent().close();
      }
      if (response.getEntity() != null) {
        response.addHeader(response.getEntity().getContentType());
        response.addHeader(response.getEntity().getContentEncoding());
        response.addHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(response.getEntity().getContentLength()));
      }
      writer.write(response);
      if (response.getEntity() != null) {
        final EntitySerializer serializer = new EntitySerializer(new LaxContentLengthStrategy());
        serializer.serialize(outputBuffer, response, response.getEntity());
      }
    } finally {
      outputBuffer.flush();
    }
  }

  @NotNull
  protected HttpResponse service(@NotNull HttpRequest req) throws IOException {
    final String pathInfo;
    try {
      pathInfo = getPathInfo(req);
    } catch (URISyntaxException e) {
      return sendError(req, HttpStatus.SC_BAD_REQUEST, e.getMessage());
    }
    final int begin = pathInfo.charAt(0) == '/' ? 1 : 0;
    final int separator = pathInfo.lastIndexOf('/');
    if (separator > 0) {
      ServiceInfo serviceInfo = holder.getService(pathInfo.substring(begin, separator));
      if (serviceInfo != null) {
        return service(req, pathInfo.substring(separator + 1), serviceInfo);
      }
    }
    return sendError(req, HttpStatus.SC_NOT_FOUND, "Service not found: " + pathInfo);
  }

  @NotNull
  public HttpResponse sendError(@NotNull HttpRequest req, int code, @NotNull String reason) {
    final BasicHttpResponse response = new BasicHttpResponse(req.getProtocolVersion(), code, reason);
    final ContentType contentType = ContentType.create("text/plain", StandardCharsets.UTF_8);
    response.setEntity(new StringEntity("ERROR " + code + ": " + reason, contentType));
    return response;
  }

  private @NotNull HttpResponse service(@NotNull HttpRequest req, @NotNull String methodPath, @NotNull ServiceInfo serviceInfo) throws IOException {
    final MethodInfo method = serviceInfo.getMethod(methodPath);
    if (method == null) {
      return sendError(req, HttpStatus.SC_NOT_FOUND, "Method not found: " + methodPath);
    }

    final Message msgRequest;
    final String httpHethod = req.getRequestLine().getMethod();
    if (httpHethod.equals("POST") && (req instanceof HttpEntityEnclosingRequest)) {
      final HttpEntity entity = ((HttpEntityEnclosingRequest) req).getEntity();
      if (entity != null) {
        msgRequest = method.requestByStream(entity.getContent(), getCharset(entity));
      } else {
        return sendError(req, HttpStatus.SC_NO_CONTENT, "Request payload not found");
      }
    } else if (httpHethod.equals("GET")) {
      Message result;
      try {
        result = method.requestByParams(getParameterMap(req));
      } catch (URISyntaxException | ParseException e) {
        return sendError(req, HttpStatus.SC_BAD_REQUEST, e.getMessage());
      }
      msgRequest = result;
    } else {
      return sendError(req, HttpStatus.SC_METHOD_NOT_ALLOWED, "Unsupported method");
    }
    try {
      final byte[] msgResponse = method.call(msgRequest, StandardCharsets.UTF_8).get();
      if (msgResponse != null) {
        final HttpResponse response = DefaultHttpResponseFactory.INSTANCE.newHttpResponse(req.getProtocolVersion(), HttpStatus.SC_OK, null);
        final ByteArrayEntity entity = new ByteArrayEntity(msgResponse, ContentType.create(method.getFormat().getMimeType(), StandardCharsets.UTF_8));
        response.setEntity(entity);
        response.setHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(entity.getContentLength()));
        response.addHeader(entity.getContentEncoding());
        response.addHeader(entity.getContentType());
        return response;
      } else {
        return sendError(req, HttpStatus.SC_INTERNAL_SERVER_ERROR, "Illegal method return value");
      }
    } catch (InterruptedException | ExecutionException e) {
      log.error("Method error " + method.getName(), e);
      return sendError(req, HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  @NotNull
  private String getPathInfo(@NotNull HttpRequest req) throws URISyntaxException {
    final URI uri = new URI(req.getRequestLine().getUri());
    return uri.getPath() != null ? uri.getPath() : "";
  }

  @NotNull
  private Map<String, String[]> getParameterMap(@NotNull HttpRequest req) throws URISyntaxException {
    final Map<String, List<String>> params = new HashMap<>();
    final List<NameValuePair> pairList = URLEncodedUtils.parse(new URI(req.getRequestLine().getUri()), StandardCharsets.UTF_8.name());
    for (NameValuePair param : pairList) {
      params.compute(param.getName(), (item, value) -> {
        if (value == null) {
          value = new ArrayList<>();
        }
        value.add(param.getValue());
        return value;
      });
    }
    final Map<String, String[]> result = new HashMap<>();
    for (Map.Entry<String, List<String>> pair : params.entrySet()) {
      result.put(pair.getKey(), pair.getValue().toArray(new String[pair.getValue().size()]));
    }
    return result;
  }

  @NotNull
  private static Charset getCharset(@NotNull HttpEntity entity) {
    final Header charset = entity.getContentEncoding();
    return charset == null ? StandardCharsets.UTF_8 : Charset.forName(charset.getValue());
  }
}
