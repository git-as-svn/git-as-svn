/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.config;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.server.LfsServer;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.local.LfsLocalStorage;

import java.io.File;
import java.io.IOException;

/**
 * Git LFS configuration file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("lfs")
public class LfsConfig implements SharedConfig {
  @NotNull
  private String path = "lfs";

  @NotNull
  public String getPath() {
    return path;
  }

  public void setPath(@NotNull String path) {
    this.path = path;
  }

  @NotNull
  public static LfsStorage getStorage(@NotNull SharedContext context) throws IOException, SVNException {
    return context.getOrCreate(LfsStorage.class, () -> new LfsConfig().createStorage(context));
  }

  @Override
  public void create(@NotNull SharedContext context) throws IOException {
    context.add(LfsStorage.class, createStorage(context));
    context.add(LfsServer.class, new LfsServer());
  }

  @NotNull
  private LfsStorage createStorage(@NotNull SharedContext context) {
    return new LfsLocalStorage(new File(context.getBasePath(), path));
  }

}
