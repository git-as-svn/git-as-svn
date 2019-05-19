/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.mapping;

import io.gitea.ApiClient;
import io.gitea.ApiException;
import io.gitea.api.UserApi;
import io.gitea.model.Repository;
import io.gitea.model.User;
import io.gitea.model.UserSearchList;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.config.GitRepositoryConfig;
import svnserver.config.RepositoryMappingConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;
import svnserver.ext.gitea.config.GiteaContext;
import svnserver.repository.RepositoryMapping;
import svnserver.repository.git.GitCreateMode;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Repository list mapping.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
@ConfigType("giteaMapping")
public final class GiteaMappingConfig implements RepositoryMappingConfig {
  @NotNull
  private GitRepositoryConfig template;
  @NotNull
  private String path;
  private DirectoryWatcher watcher;
  private int cacheTimeSec = 15;
  private int cacheMaximumSize = 1000;

  public GiteaMappingConfig() {
    this("/var/git/repositories/", GitCreateMode.ERROR);
  }

  private GiteaMappingConfig(@NotNull String path, @NotNull GitCreateMode createMode) {
    this.path = path;
    template = new GitRepositoryConfig(createMode);
  }

  public GiteaMappingConfig(@NotNull File path, @NotNull GitCreateMode createMode) {
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
  public RepositoryMapping create(@NotNull SharedContext context, boolean canUseParallelIndexing) throws IOException, SVNException {
    final GiteaContext giteaContext = context.sure(GiteaContext.class);
    final ApiClient apiClient = giteaContext.connect();
    final UserApi userApi = new UserApi(apiClient);
    // Get repositories.

    final GiteaMapping mapping = new GiteaMapping(context, this);

    try {
      final UserSearchList usersList = userApi.userSearch(null, null, null);

      for (User u : usersList.getData()) {
        List<Repository> repositories = userApi.userListRepos(u.getLogin());
        for (Repository repository : repositories) {
          mapping.addRepository(repository);
        }
      }
    } catch (ApiException e) {
      throw new RuntimeException("Failed to initialize", e);
    }

    // Add directory watcher
    if (watcher == null || !watcher.isAlive()) {
      watcher = new DirectoryWatcher(path, new GiteaMapper(apiClient, mapping));
    }

    final Consumer<GiteaProject> init = repository -> {
      try {
        repository.initRevisions();
      } catch (IOException | SVNException e) {
        throw new RuntimeException(String.format("[%s]: failed to initialize", repository.getContext().getName()), e);
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
