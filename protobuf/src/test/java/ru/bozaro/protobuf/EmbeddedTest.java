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
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.io.AbstractSessionInputBuffer;
import org.apache.http.impl.io.HttpRequestParser;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.message.BasicLineParser;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
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
    HttpParams httpParams = new BasicHttpParams();
    SessionInputBuffer inputBuffer = new StreamInputBuffer(stream, httpParams);
    HttpRequestParser parser = new HttpRequestParser(inputBuffer,
        new BasicLineParser(),
        new DefaultHttpRequestFactory(),
        httpParams
    );
    HttpMessage message = parser.parse();
  }

  private static class StreamInputBuffer extends AbstractSessionInputBuffer {
    @NotNull
    private final InputStream stream;

    public StreamInputBuffer(@NotNull InputStream stream, @NotNull HttpParams params) {
      this.stream = stream;
      init(stream, 1024, params);
    }

    @Override
    public boolean isDataAvailable(int timeout) throws IOException {
      return stream.available() > 0;
    }
  }
}
