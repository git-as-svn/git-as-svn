/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import ru.bozaro.gitlfs.server.ContentManager;
import ru.bozaro.gitlfs.server.ContentServlet;
import ru.bozaro.gitlfs.server.PointerServlet;
import svnserver.context.Local;
import svnserver.context.LocalContext;
import svnserver.context.Shared;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.web.server.WebServer;

import javax.servlet.Servlet;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;

/**
 * LFS server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsServer implements Shared {
  @NotNull
  public static final String SERVLET_BASE = "info/lfs";
  @NotNull
  public static final String SERVLET_AUTH = "auth/lfs";
  @NotNull
  public static final String SERVLET_CONTENT = SERVLET_BASE + "/storage";
  @NotNull
  public static final String SERVLET_POINTER = SERVLET_BASE + "/objects";
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

    final String pathSpec = ("/" + MessageFormat.format(pathFormat, name) + "/").replaceAll("/+", "/");
    final ContentManager manager = new LfsContentManager(localContext, storage);
    final Collection<WebServer.ServletInfo> servletsInfo = webServer.addServlets(
        ImmutableMap.<String, Servlet>builder()
            .put(pathSpec + SERVLET_AUTH, new LfsAuthServlet(localContext, pathSpec + SERVLET_BASE, privateToken))
            .put(pathSpec + SERVLET_POINTER + "/*", new PointerServlet(manager, pathSpec + SERVLET_CONTENT))
            .put(pathSpec + SERVLET_CONTENT + "/*", new ContentServlet(manager))
            .build()
    );
    localContext.add(LfsServerHolder.class, new LfsServerHolder(webServer, servletsInfo));
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
    }
  }
}
