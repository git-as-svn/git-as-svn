/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import org.jetbrains.annotations.NotNull;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.web.server.WebServer;

/**
 * Base LFS servlet.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public abstract class LfsAbstractResource {
  @NotNull
  public static final String MIME_TYPE = "application/vnd.git-lfs+json";

  @NotNull
  private final LocalContext context;
  @NotNull
  private final LfsStorage storage;

  public LfsAbstractResource(@NotNull LocalContext context, @NotNull LfsStorage storage) {
    this.context = context;
    this.storage = storage;
  }

  @NotNull
  public LocalContext getContext() {
    return context;
  }

  @NotNull
  public SharedContext getShared() {
    return context.getShared();
  }

  @NotNull
  public WebServer getWebServer() {
    return context.getShared().sure(WebServer.class);
  }

  @NotNull
  public LfsStorage getStorage() {
    return storage;
  }
}
