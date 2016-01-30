/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf;

import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.io.*;
import org.apache.http.io.HttpMessageParser;
import org.apache.http.io.HttpMessageWriter;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.io.SessionOutputBuffer;
import org.apache.http.message.BasicLineParser;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Socket wrapper for Protobuf RPC
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ProtobufRpcSocket extends ProtobufRpcSimpleHttp implements AutoCloseable {
  private static final @NotNull Logger log = LoggerFactory.getLogger(ProtobufRpcSocket.class);
  @NotNull
  private final Thread thread;
  @NotNull
  private final ServerSocket socket;
  @NotNull
  private final Map<Socket, Boolean> connections = new ConcurrentHashMap<>();

  @NotNull
  private final ExecutorService pool;

  public ProtobufRpcSocket(@NotNull ServiceHolder holder, @NotNull ServerSocket socket, @NotNull ExecutorService pool) {
    super(holder);
    this.socket = socket;
    this.pool = pool;
    this.thread = new Thread(ProtobufRpcSocket.this::acceptThread);
    thread.setDaemon(true);
    thread.start();
  }

  protected void acceptThread() {
    while (!socket.isClosed()) {
      try {
        final Socket clientSocket = socket.accept();
        connections.put(clientSocket, Boolean.FALSE);
        pool.execute(() -> {
          try (final Socket client = clientSocket) {
            acceptClient(client);
          } catch (IOException e) {
            log.error(e.getMessage(), e);
          } finally {
            connections.remove(clientSocket);
          }
        });
      } catch (IOException e) {
        log.error(e.getMessage(), e);
      }
    }
  }

  protected void acceptClient(@NotNull Socket client) throws IOException {
    final SessionInputBuffer inputBuffer = wrapInputStream(client.getInputStream());
    final HttpMessageParser<HttpRequest> parser = new DefaultHttpRequestParser(inputBuffer,
        new BasicLineParser(),
        new DefaultHttpRequestFactory(),
        MessageConstraints.DEFAULT
    );
    final SessionOutputBuffer outputBuffer = wrapOutputStream(client.getOutputStream());
    final HttpMessageWriter<HttpResponse> writer = new DefaultHttpResponseWriter(outputBuffer);
    while (!socket.isClosed()) {
      try {
        service(inputBuffer, outputBuffer, parser, writer);
      } catch (ConnectionClosedException ignored) {
        break;
      } catch (HttpException e) {
        log.error(e.getMessage(), e);
        break;
      }
    }
  }

  @NotNull
  private SessionOutputBuffer wrapOutputStream(@NotNull OutputStream outputStream) {
    return new SessionOutputBufferImpl(new HttpTransportMetricsImpl(), 1024) {{
      bind(outputStream);
    }};
  }

  @NotNull
  protected SessionInputBuffer wrapInputStream(@NotNull InputStream inputStream) {
    return new SessionInputBufferImpl(new HttpTransportMetricsImpl(), 1024) {{
      bind(inputStream);
    }};
  }

  @Override
  public void close() throws Exception {
    socket.close();
    for (Socket clinet : connections.keySet()) {
      clinet.close();
    }
    thread.join();
  }

}
