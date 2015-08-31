/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.context.Shared;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.web.server.WebServer;

import java.io.IOException;

/**
 * LFS server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsServer implements Shared {
  @NotNull
  private final SharedContext context;
  @Nullable
  private WebServer webServer;
  @Nullable
  private String privateToken;

  public LfsServer(@NotNull SharedContext context, @Nullable String privateToken) {
    this.context = context;
    this.privateToken = privateToken;
  }

  @Override
  public void init(@NotNull SharedContext context) throws IOException, SVNException {
    this.webServer = WebServer.get(context);
  }

  public void register(@NotNull String name, @NotNull LfsStorage storage) {
    if (webServer == null) throw new IllegalStateException("Object is non-initialized");
    if (privateToken != null) {
      webServer.addServlet("/" + name + ".git/info/lfs/auth", new LfsAuthServlet(context, storage, privateToken));
    }
    webServer.addServlet("/" + name + ".git/info/lfs/objects/*", new LfsObjectsServlet(context, storage));
    webServer.addServlet("/" + name + ".git/info/lfs/storage/*", new LfsStorageServlet(context, storage));
  }

  public void unregister(@NotNull String name) {
    if (webServer == null) throw new IllegalStateException("Object is non-initialized");
    webServer.removeServlet("/" + name + ".git/info/lfs/storage/*");
    webServer.removeServlet("/" + name + ".git/info/lfs/objects/*");
    if (privateToken != null) {
      webServer.removeServlet("/" + name + ".git/info/lfs/auth");
    }
  }
}
