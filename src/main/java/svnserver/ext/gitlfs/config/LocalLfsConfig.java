/*
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
import svnserver.repository.locks.LocalLockManager;

import java.io.File;

/**
 * Git LFS configuration file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("localLfs")
public final class LocalLfsConfig implements SharedConfig, LfsStorageFactory {
  // Default client token expiration time.
  public static final int DEFAULT_TOKEN_EXPIRE_SEC = 3600;
  // Allow batch API request only if token is not expired in token ensure time (part of tokenExpireTime).
  private static final float DEFAULT_TOKEN_ENSURE_TIME = 0.5f;

  @NotNull
  private String path = "lfs";
  private int tokenExpireSec = DEFAULT_TOKEN_EXPIRE_SEC;
  private float tokenEnsureTime = DEFAULT_TOKEN_ENSURE_TIME;
  private boolean compress = true;
  private boolean saveMeta = true;
  @NotNull
  private String secretToken = "";
  @NotNull
  private LfsLayout layout = LfsLayout.OneLevel;

  @Override
  public void create(@NotNull SharedContext context) {
    context.add(LfsStorageFactory.class, this);
    context.add(LfsServer.class, new LfsServer(secretToken, tokenExpireSec, tokenEnsureTime));
  }

  @NotNull
  public LfsStorage createStorage(@NotNull LocalContext context) {
    final File dataRoot = ConfigHelper.joinPath(context.getShared().getBasePath(), path);

    return new LfsLocalStorage(
        LocalLockManager.getPersistentStorage(context),
        layout,
        dataRoot,
        saveMeta ? LfsLocalStorage.getMetaRoot(context) : null,
        compress
    );
  }

  /**
   * Git LFS file layout.
   *
   * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
   */
  public enum LfsLayout {
    OneLevel {
      @NotNull
      @Override
      public String getPath(@NotNull String hash) {
        return hash.substring(0, 2) + "/" + hash;
      }
    },
    TwoLevels {
      @NotNull
      @Override
      public String getPath(@NotNull String hash) {
        return hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + hash;
      }
    },
    GitLab {
      @NotNull
      @Override
      public String getPath(@NotNull String hash) {
        return hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + hash.substring(4);
      }
    };

    @NotNull
    public abstract String getPath(@NotNull String hash);
  }
}
