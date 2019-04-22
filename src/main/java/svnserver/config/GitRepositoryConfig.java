/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import svnserver.context.LocalContext;
import svnserver.repository.git.GitCreateMode;
import svnserver.repository.git.GitLocation;
import svnserver.repository.git.GitRepository;
import svnserver.repository.git.push.GitPushEmbeddedConfig;

import java.io.File;
import java.io.IOException;

/**
 * Repository configuration.
 *
 * @author a.navrotskiy
 */
@SuppressWarnings("FieldCanBeLocal")
public final class GitRepositoryConfig {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitRepositoryConfig.class);
  @NotNull
  private String branch = "master";
  @NotNull
  private String path = ".git";
  @NotNull
  private GitPusherConfig pusher = GitPushEmbeddedConfig.instance;
  @NotNull
  private GitCreateMode createMode;

  private boolean renameDetection = true;

  public GitRepositoryConfig() {
    this(GitCreateMode.ERROR);
  }

  public GitRepositoryConfig(@NotNull GitCreateMode createMode) {
    this.createMode = createMode;
  }

  @NotNull
  public GitRepository create(@NotNull LocalContext context) throws IOException {
    return create(context, ConfigHelper.joinPath(context.getShared().getBasePath(), path));
  }

  @NotNull
  public GitRepository create(@NotNull LocalContext context, @NotNull File fullPath) throws IOException {
    context.add(GitLocation.class, new GitLocation(fullPath));

    return new GitRepository(context, createRepository(context, fullPath), pusher.create(context), branch, renameDetection);
  }

  @NotNull
  private Repository createRepository(@NotNull LocalContext context, @NotNull File fullPath) throws IOException {
    if (!fullPath.exists()) {
      log.info("[{}]: storage {} not found, create mode: {}", context.getName(), fullPath, createMode);
      return createMode.createRepository(fullPath, branch);
    }
    log.info("[{}]: using existing storage {}", context.getName(), fullPath);
    return new FileRepository(fullPath);
  }
}
