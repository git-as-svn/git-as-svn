/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.context.Local;
import svnserver.context.LocalContext;
import svnserver.context.Shared;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.web.server.WebServer;

import java.io.IOException;
import java.text.MessageFormat;

/**
 * LFS server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsServer implements Shared {
  @NotNull
  public static final String SERVLET_BASE = "info/lfs";
  @NotNull
  private final String pathFormat;
  @Nullable
  private final String privateToken;

  public LfsServer(@NotNull String pathFormat, @Nullable String privateToken) {
    this.pathFormat = pathFormat;
    this.privateToken = privateToken;
  }

  public void register(@NotNull LocalContext localContext, @NotNull LfsStorage storage) throws IOException, SVNException {
    final WebServer webServer = WebServer.get(localContext.getShared());
    final String name = localContext.getName();

    final ResourceConfig rc = WebServer.createResourceConfig(localContext);
    rc.register(new LfsAuthResource(localContext, storage, privateToken));
    rc.register(new LfsObjectsResource(localContext, storage));
    rc.register(new LfsStorageResource(localContext, storage));

    final String pathSpec = "/" + MessageFormat.format(pathFormat, name) + "/*";
    final WebServer.ServletInfo servletInfo = webServer.addServlet(pathSpec.replaceAll("/+", "/"), new ServletContainer(rc));
    localContext.add(LfsServerHolder.class, new LfsServerHolder(webServer, servletInfo));
  }

  public void unregister(@NotNull LocalContext localContext) throws IOException, SVNException {
    LfsServerHolder holder = localContext.remove(LfsServerHolder.class);
    if (holder != null) {
      holder.close();
    }
  }

  private static class LfsServerHolder implements Local {
    @NotNull
    private final WebServer webServer;
    @NotNull
    private final WebServer.ServletInfo servlet;

    public LfsServerHolder(@NotNull WebServer webServer, @NotNull WebServer.ServletInfo servlet) {
      this.webServer = webServer;
      this.servlet = servlet;
    }

    @Override
    public void close() {
      webServer.removeServlet(servlet);
    }
  }
}
