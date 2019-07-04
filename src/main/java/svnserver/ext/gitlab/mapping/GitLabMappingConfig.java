/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping;

import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabProject;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.config.GitRepositoryConfig;
import svnserver.config.RepositoryMappingConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;
import svnserver.ext.gitlab.config.GitLabContext;
import svnserver.repository.RepositoryMapping;
import svnserver.repository.git.GitCreateMode;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Repository list mapping.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("gitlabMapping")
public final class GitLabMappingConfig implements RepositoryMappingConfig {
  @NotNull
  private GitRepositoryConfig template;
  @NotNull
  private String path;
  private int cacheTimeSec = 15;
  private int cacheMaximumSize = 1000;

  public GitLabMappingConfig() {
    this("/var/git/repositories/", GitCreateMode.ERROR);
  }

  private GitLabMappingConfig(@NotNull String path, @NotNull GitCreateMode createMode) {
    this.path = path;
    this.template = new GitRepositoryConfig(createMode);
  }

  public GitLabMappingConfig(@NotNull File path, @NotNull GitCreateMode createMode) {
    this(path.getAbsolutePath(), createMode);
  }

  @NotNull
  GitRepositoryConfig getTemplate() {
    return template;
  }

  @NotNull
  String getPath() {
    return path;
  }

  int getCacheTimeSec() {
    return cacheTimeSec;
  }

  int getCacheMaximumSize() {
    return cacheMaximumSize;
  }

  @NotNull
  @Override
  public RepositoryMapping create(@NotNull SharedContext context, boolean canUseParallelIndexing) throws IOException {
    final GitLabContext gitlab = context.sure(GitLabContext.class);
    final GitlabAPI api = gitlab.connect();
    // Get repositories.

    final GitLabMapping mapping = new GitLabMapping(context, this, gitlab);
    for (GitlabProject project : api.getProjects())
      mapping.updateRepository(project);

    final Consumer<GitLabProject> init = repository -> {
      try {
        repository.initRevisions();
      } catch (IOException | SVNException e) {
        throw new RuntimeException(String.format("[%s]: failed to initialize", repository), e);
      }
    };

    if (canUseParallelIndexing) {
      mapping.getMapping().values().parallelStream().forEach(init);
    } else {
      mapping.getMapping().values().forEach(init);
    }

    return mapping;
  }
}
