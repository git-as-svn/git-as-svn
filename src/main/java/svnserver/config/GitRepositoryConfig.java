/**
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
import org.tmatesoft.svn.core.SVNException;
import svnserver.config.serializer.ConfigType;
import svnserver.context.LocalContext;
import svnserver.repository.VcsRepository;
import svnserver.repository.git.GitCreateMode;
import svnserver.repository.git.GitLocation;
import svnserver.repository.git.GitRepository;
import svnserver.repository.git.push.GitPushEmbeddedConfig;
import svnserver.repository.locks.PersistentLockFactory;

import java.io.File;
import java.io.IOException;

/**
 * Repository configuration.
 *
 * @author a.navrotskiy
 */
@SuppressWarnings("FieldCanBeLocal")
@ConfigType("git")
public final class GitRepositoryConfig implements RepositoryConfig {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitRepositoryConfig.class);
  @NotNull
  private String branch = "master";
  @NotNull
  private String path = ".git";
  @NotNull
  private GitPusherConfig pusher = GitPushEmbeddedConfig.instance;
  @NotNull
  private GitCreateMode createMode = GitCreateMode.ERROR;
  private boolean renameDetection = true;

  @NotNull
  public GitPusherConfig getPusher() {
    return pusher;
  }

  public boolean isRenameDetection() {
    return renameDetection;
  }

  @NotNull
  public Repository createRepository(@NotNull File fullPath) throws IOException {
    if (!fullPath.exists()) {
      log.info("Repository fullPath: {} - not exists, create mode: {}", fullPath, createMode);
      return createMode.createRepository(fullPath, branch);
    }
    log.info("Repository fullPath: {}", fullPath);
    return new FileRepository(fullPath);
  }

  @NotNull
  @Override
  public VcsRepository create(@NotNull LocalContext context) throws IOException, SVNException {
    return create(context, ConfigHelper.joinPath(context.getShared().getBasePath(), path));
  }

  @NotNull
  public VcsRepository create(@NotNull LocalContext context, @NotNull File fullPath) throws IOException, SVNException {
    context.add(GitLocation.class, new GitLocation(fullPath));
    return new GitRepository(context, createRepository(fullPath), getPusher().create(), branch, isRenameDetection(), new PersistentLockFactory(context));
  }
}
