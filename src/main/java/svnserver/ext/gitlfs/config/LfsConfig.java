/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.server.LfsServer;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsStorageFactory;
import svnserver.ext.gitlfs.storage.local.LfsLocalStorage;
import svnserver.repository.git.GitLocation;

import java.io.File;
import java.io.IOException;

/**
 * Git LFS configuration file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("lfs")
public class LfsConfig implements SharedConfig, LfsStorageFactory {
  @NotNull
  private String path = "lfs";
  @NotNull
  private String pathFormat = "{0}.git";
  private boolean compress = true;
  @Nullable
  private String token;

  @NotNull
  public String getPath() {
    return path;
  }

  public void setPath(@NotNull String path) {
    this.path = path;
  }

  @NotNull
  public String getPathFormat() {
    return pathFormat;
  }

  public void setPathFormat(@NotNull String pathFormat) {
    this.pathFormat = pathFormat;
  }

  public boolean isCompress() {
    return compress;
  }

  public void setCompress(boolean compress) {
    this.compress = compress;
  }

  @NotNull
  public static LfsStorage getStorage(@NotNull LocalContext context) throws IOException, SVNException {
    return context.getShared().getOrCreate(LfsStorageFactory.class, LfsConfig::new).createStorage(context);
  }

  @Override
  public void create(@NotNull SharedContext context) throws IOException {
    context.add(LfsStorageFactory.class, this);
    context.add(LfsServer.class, new LfsServer(pathFormat, token));
  }

  @NotNull
  public LfsStorage createStorage(@NotNull LocalContext context) {
    File dataRoot = new File(context.getShared().getBasePath(), path);
    File metaRoot = new File(context.sure(GitLocation.class).getFullPath(), "lfs/meta");
    return new LfsLocalStorage(dataRoot, metaRoot, isCompress());
  }

}
