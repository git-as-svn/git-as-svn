/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package ru.bozaro.protobuf;

import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jetbrains.annotations.NotNull;

import javax.servlet.Servlet;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Embedded HTTP server for servlet testing.
 *
 * @author Artem V. Navrotskiy
 */
public class EmbeddedHttpServer implements AutoCloseable {
  @NotNull
  private final Server server;
  @NotNull
  private final ServerConnector http;
  @NotNull
  private final ServletHandler servletHandler;

  public EmbeddedHttpServer() throws Exception {
    this.server = new Server();
    this.http = new ServerConnector(server, new HttpConnectionFactory());
    http.setPort(0);
    http.setHost("127.0.1.1");
    http.setIdleTimeout(30000);
    server.addConnector(http);

    this.servletHandler = new ServletHandler();
    server.setHandler(servletHandler);

    server.start();
  }

  @NotNull
  public URI getBase() {
    try {
      return new URI("http", null, http.getHost(), http.getLocalPort(), null, null, null);
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  public void addServlet(@NotNull String pathSpec, @NotNull Servlet servlet) {
    servletHandler.addServletWithMapping(new ServletHolder(servlet), pathSpec);
  }

  @Override
  public void close() throws Exception {
    server.stop();
    server.join();
  }
}
