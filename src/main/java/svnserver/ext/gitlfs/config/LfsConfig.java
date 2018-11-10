/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.config;

import org.jetbrains.annotations.NotNull;
import svnserver.config.ConfigHelper;
import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.server.LfsServer;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsStorageFactory;
import svnserver.ext.gitlfs.storage.local.LfsLocalStorage;
import svnserver.ext.gitlfs.storage.network.LfsHttpStorage;
import svnserver.repository.git.GitLocation;

import java.io.File;

import static ru.bozaro.gitlfs.common.Constants.HEADER_AUTHORIZATION;

/**
 * Git LFS configuration file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("lfs")
public final class LfsConfig implements SharedConfig, LfsStorageFactory {
  // Default client token expiration time.
  public static final int DEFAULT_TOKEN_EXPIRE_SEC = 3600;
  // Allow batch API request only if token is not expired in token ensure time (part of tokenExpireTime).
  private static final float DEFAULT_TOKEN_ENSURE_TIME = 0.5f;

  private String path = "lfs";
  private int tokenExpireSec = DEFAULT_TOKEN_EXPIRE_SEC;
  private float tokenEnsureTime = DEFAULT_TOKEN_ENSURE_TIME;
  private boolean compress = true;
  private boolean saveMeta = true;
  @NotNull
  private String secretToken = "";
  @NotNull
  private LfsLayout layout = LfsLayout.OneLevel;
  private String tokenHeader = HEADER_AUTHORIZATION;
  private String tokenPrefix = "Basic ";
  private String scheme = "https";

  @Override
  public void create(@NotNull SharedContext context) {
    context.add(LfsStorageFactory.class, this);
    if (layout != LfsLayout.HTTP)
      context.add(LfsServer.class, new LfsServer(secretToken, tokenExpireSec, tokenEnsureTime));
  }

  @NotNull
  public LfsStorage createStorage(@NotNull LocalContext context) {
    if (layout == LfsLayout.HTTP) {
      return new LfsHttpStorage(this);
    } else {
      File dataRoot = ConfigHelper.joinPath(context.getShared().getBasePath(), path);
      File metaRoot = saveMeta ? new File(context.sure(GitLocation.class).getFullPath(), "lfs/meta") : null;
      return new LfsLocalStorage(getLayout(), dataRoot, metaRoot, compress);
    }
  }

  public String getTokenHeader() {
    return tokenHeader;
  }

  public String getTokenPrefix() {
    return tokenPrefix;
  }

  public String getPath() {
    return path;
  }

  public String getScheme() {
    return scheme;
  }

  @NotNull
  public LfsLayout getLayout() {
    return layout;
  }
}
