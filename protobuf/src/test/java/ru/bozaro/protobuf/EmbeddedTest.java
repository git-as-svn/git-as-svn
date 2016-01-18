/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf;

import org.apache.http.HttpException;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.io.AbstractMessageParser;
import org.apache.http.impl.io.DefaultHttpRequestParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.message.BasicLineParser;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * Embedded HTTP parser example.
 *
 * @author Artem V. Navrotskiy
 */
public class EmbeddedTest {
  @Test
  public void parseRequest() throws IOException, HttpException, URISyntaxException {
    ByteArrayInputStream stream = new ByteArrayInputStream("GET /foo.bar HTTP/1.1\nHost: yandex.ru\n\n".getBytes(StandardCharsets.UTF_8));
    SessionInputBuffer inputBuffer = new StreamInputBuffer(stream);
    AbstractMessageParser<HttpRequest> parser = new DefaultHttpRequestParser(inputBuffer,
        new BasicLineParser(),
        new DefaultHttpRequestFactory(),
        MessageConstraints.DEFAULT
    );
    HttpMessage message = parser.parse();
  }

  private static class StreamInputBuffer extends SessionInputBufferImpl {
    public StreamInputBuffer(@NotNull InputStream stream) {
      super(new HttpTransportMetricsImpl(), 1024);
      bind(stream);
    }
  }
}
