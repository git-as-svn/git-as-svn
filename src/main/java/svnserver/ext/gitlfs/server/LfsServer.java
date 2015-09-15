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

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * LFS server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsServer implements Shared {
  @NotNull
  public static final String SERVLET_BASE = ".git/info/lfs";
  @NotNull
  public static final String SERVLET_OBJECTS = SERVLET_BASE + "/objects/";
  @NotNull
  public static final String SERVLET_STORAGE = SERVLET_BASE + "/storage/";

  @Nullable
  private String privateToken;

  public LfsServer(@Nullable String privateToken) {
    this.privateToken = privateToken;
  }

  public void register(@NotNull LocalContext localContext, @NotNull LfsStorage storage) throws IOException, SVNException {
    final WebServer webServer = WebServer.get(localContext.getShared());
    final String name = localContext.getName();
    final Map<String, Servlet> servlets = new TreeMap<>();

    final ResourceConfig rc = WebServer.createResourceConfig(localContext);
    rc.register(new LfsAuthResource(localContext, storage, privateToken));
    rc.register(new LfsObjectsResource(localContext, storage));
    rc.register(new LfsStorageResource(localContext, storage));
    servlets.put("/" + name + ".git/*", new ServletContainer(rc));

    localContext.add(LfsServerHolder.class, new LfsServerHolder(webServer, webServer.addServlets(servlets)));
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
    private final Collection<WebServer.ServletInfo> servlets;

    public LfsServerHolder(@NotNull WebServer webServer, @NotNull Collection<WebServer.ServletInfo> servlets) {
      this.webServer = webServer;
      this.servlets = servlets;
    }

    @Override
    public void close() {
      webServer.removeServlets(servlets);
      servlets.clear();
    }
  }
}
