/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.config;

import org.jetbrains.annotations.NotNull;
import svnserver.config.ConfigHelper;
import svnserver.config.serializer.ConfigType;
import svnserver.context.LocalContext;
import svnserver.ext.gitlfs.config.LocalLfsConfig;
import svnserver.ext.gitlfs.storage.local.LfsLocalReader;

import java.nio.file.Path;

@ConfigType("fileLfs")
public final class FileLfsMode implements LfsMode {
  @NotNull
  private String path = "/var/opt/gitlab/gitlab-rails/shared/lfs-objects";

  @NotNull
  @Override
  public LfsReaderFactory readerFactory(@NotNull LocalContext context) {
    final Path dataRoot = ConfigHelper.joinPath(context.getShared().getBasePath(), path);
    return oid -> LfsLocalReader.create(LocalLfsConfig.LfsLayout.GitLab, dataRoot, null, oid);
  }
}
