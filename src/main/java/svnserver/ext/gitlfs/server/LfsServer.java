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
import svnserver.api.lfs.Lfs;
import svnserver.context.Local;
import svnserver.context.LocalContext;
import svnserver.context.Shared;
import svnserver.ext.api.ServiceRegistry;
import svnserver.ext.gitlfs.api.LfsRpc;
import svnserver.ext.gitlfs.config.LfsConfig;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.web.server.WebServer;

import javax.servlet.Servlet;
import java.io.IOException;
import java.net.URI;
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
  private int tokenExpireSec;
  private int tokenEnsureSec;

  public LfsServer(@NotNull String pathFormat, @Nullable String privateToken, int tokenExpireSec, int tokenEnsureSec) {
    this.pathFormat = pathFormat;
    this.privateToken = privateToken;
    this.tokenExpireSec = tokenExpireSec > 0 ? tokenExpireSec : LfsConfig.DEFAULT_TOKEN_EXPIRE_SEC;
    this.tokenEnsureSec = (tokenEnsureSec > 0) && (tokenEnsureSec < this.tokenExpireSec / 2) ? tokenEnsureSec : this.tokenExpireSec / 2;
  }

  public void register(@NotNull LocalContext localContext, @NotNull LfsStorage storage) throws IOException, SVNException {
    final WebServer webServer = WebServer.get(localContext.getShared());
    final String name = localContext.getName();

    final String pathSpec = ("/" + MessageFormat.format(pathFormat, name) + "/").replaceAll("/+", "/");
    final ContentManager pointerManager = new LfsContentManager(localContext, storage, tokenExpireSec, tokenEnsureSec);
    final ContentManager contentManager = new LfsContentManager(localContext, storage, tokenExpireSec, 0);
    final Collection<WebServer.Holder> servletsInfo = webServer.addServlets(
        ImmutableMap.<String, Servlet>builder()
            .put(pathSpec + SERVLET_AUTH, new LfsAuthServlet(localContext, pathSpec + SERVLET_BASE, privateToken, tokenExpireSec))
            .put(pathSpec + SERVLET_POINTER + "/*", new PointerServlet(pointerManager, pathSpec + SERVLET_CONTENT))
            .put(pathSpec + SERVLET_CONTENT + "/*", new ContentServlet(contentManager))
            .build()
    );
    localContext.add(LfsServerHolder.class, new LfsServerHolder(webServer, servletsInfo));
    ServiceRegistry.get(localContext).addService(Lfs.newReflectiveBlockingService(new LfsRpc(URI.create(pathSpec + SERVLET_BASE), localContext, tokenExpireSec)));
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
    private final Collection<WebServer.Holder> servlets;

    public LfsServerHolder(@NotNull WebServer webServer, @NotNull Collection<WebServer.Holder> servlets) {
      this.webServer = webServer;
      this.servlets = servlets;
    }

    @Override
    public void close() {
      webServer.removeServlets(servlets);
    }
  }
}
