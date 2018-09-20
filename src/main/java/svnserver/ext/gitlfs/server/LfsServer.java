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
import ru.bozaro.gitlfs.server.ContentManager;
import ru.bozaro.gitlfs.server.ContentServlet;
import ru.bozaro.gitlfs.server.PointerServlet;
import svnserver.context.Local;
import svnserver.context.LocalContext;
import svnserver.context.Shared;
import svnserver.ext.gitlfs.config.LfsConfig;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.web.server.WebServer;

import javax.servlet.Servlet;
import java.text.MessageFormat;
import java.util.Collection;

/**
 * LFS server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class LfsServer implements Shared {
  @NotNull
  private static final String SERVLET_BASE = "info/lfs";
  @NotNull
  public static final String SERVLET_AUTH = "lfs_authenticate";
  @NotNull
  private static final String SERVLET_CONTENT = SERVLET_BASE + "/storage";
  @NotNull
  private static final String SERVLET_POINTER = SERVLET_BASE + "/objects";
  @NotNull
  private final String secretToken;
  private int tokenExpireSec;
  private float tokenEnsureTime;

  public LfsServer(@NotNull String secretToken, int tokenExpireSec, float tokenEnsureTime) {
    this.secretToken = secretToken;
    this.tokenExpireSec = tokenExpireSec > 0 ? tokenExpireSec : LfsConfig.DEFAULT_TOKEN_EXPIRE_SEC;
    this.tokenEnsureTime = Math.max(0.0f, Math.min(tokenEnsureTime, 1.0f));
  }

  public void register(@NotNull LocalContext localContext, @NotNull LfsStorage storage) {
    final WebServer webServer = WebServer.get(localContext.getShared());
    final String name = localContext.getName();

    final String pathSpec =  String.format("/%s.git/", name).replaceAll("/+", "/");
    final ContentManager pointerManager = new LfsContentManager(localContext, storage, tokenExpireSec, tokenEnsureTime);
    final ContentManager contentManager = new LfsContentManager(localContext, storage, tokenExpireSec, 0.0f);
    final Collection<WebServer.Holder> servletsInfo = webServer.addServlets(
        ImmutableMap.<String, Servlet>builder()
            .put(pathSpec + SERVLET_AUTH, new LfsAuthServlet(localContext, pathSpec + SERVLET_BASE, secretToken, tokenExpireSec, tokenEnsureTime))
            .put(pathSpec + SERVLET_POINTER + "/*", new PointerServlet(pointerManager, pathSpec + SERVLET_CONTENT))
            .put(pathSpec + SERVLET_CONTENT + "/*", new ContentServlet(contentManager))
            .build()
    );
    localContext.add(LfsServerHolder.class, new LfsServerHolder(webServer, servletsInfo));
  }

  void unregister(@NotNull LocalContext localContext) {
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

    LfsServerHolder(@NotNull WebServer webServer, @NotNull Collection<WebServer.Holder> servlets) {
      this.webServer = webServer;
      this.servlets = servlets;
    }

    @Override
    public void close() {
      webServer.removeServlets(servlets);
    }
  }
}
