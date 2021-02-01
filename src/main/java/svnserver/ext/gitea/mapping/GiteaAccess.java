/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.mapping;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.gitea.ApiClient;
import io.gitea.ApiException;
import io.gitea.api.RepositoryApi;
import io.gitea.model.Permission;
import io.gitea.model.Repository;
import org.jetbrains.annotations.NotNull;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.ext.gitea.config.GiteaContext;
import svnserver.repository.VcsAccess;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Access control by Gitea server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
final class GiteaAccess implements VcsAccess {
  @NotNull
  private final LoadingCache<String, Repository> cache;
  @NotNull
  private final Repository repository;

  GiteaAccess(@NotNull LocalContext local, @NotNull GiteaMappingConfig config, @NotNull Repository repository) {
    this.repository = repository;
    final long projectId = repository.getId();

    final GiteaContext context = GiteaContext.sure(local.getShared());

    this.cache = CacheBuilder.newBuilder().maximumSize(config.getCacheMaximumSize())
        .expireAfterWrite(config.getCacheTimeSec(), TimeUnit.SECONDS).build(new CacheLoader<String, Repository>() {
          @Override
          public Repository load(@NotNull String username) throws Exception {
            if (username.isEmpty()) {
              try {
                final ApiClient apiClient = context.connect();
                final RepositoryApi repositoryApi = new RepositoryApi(apiClient);
                final Repository repository = repositoryApi.repoGetByID(projectId);
                if (!repository.isPrivate()) {
                  // Munge the permissions
                  repository.getPermissions().setAdmin(false);
                  repository.getPermissions().setPush(false);
                  return repository;
                }
                throw new FileNotFoundException();
              } catch (ApiException e) {
                if (e.getCode() == 404) {
                  throw new FileNotFoundException();
                } else {
                  throw e;
                }
              }
            }
            // Sudo as the user
            try {
              final ApiClient apiClient = context.connect(username);
              final RepositoryApi repositoryApi = new RepositoryApi(apiClient);

              return repositoryApi.repoGetByID(projectId);
            } catch (ApiException e) {
              if (e.getCode() == 404) {
                throw new FileNotFoundException();
              } else {
                throw e;
              }
            }
          }
        });
  }

  @Override
  public boolean canRead(@NotNull User user, @NotNull String branch, @NotNull String path) throws IOException {
    try {
      Repository repository = getCachedProject(user);
      if (!repository.isPrivate())
        return true;

      final Permission permission = repository.getPermissions();
      return permission.isAdmin() || permission.isPull();
    } catch (FileNotFoundException ignored) {
      return false;
    }
  }

  @Override
  public boolean canWrite(@NotNull User user, @NotNull String branch, @NotNull String path) throws IOException {
    if (user.isAnonymous())
      return false;

    try {
      final Repository repository = getCachedProject(user);
      final Permission permission = repository.getPermissions();
      return permission.isAdmin() || permission.isPush();
    } catch (FileNotFoundException ignored) {
      return false;
    }
  }

  @Override
  public void updateEnvironment(@NotNull Map<String, String> environment, @NotNull User user) {
    environment.put("GITEA_REPO_ID", "" + repository.getId());
    environment.put("GITEA_REPO_IS_WIKI", "false");
    environment.put("GITEA_REPO_NAME", repository.getName());
    environment.put("GITEA_REPO_USER", repository.getOwner().getLogin());

    final String externalId = user.getExternalId();
    environment.put("SSH_ORIGINAL_COMMAND", "git");
    environment.put("GITEA_PUSHER_EMAIL", user.getEmail());
    if (externalId != null) {
      environment.put("GITEA_PUSHER_ID", user.getExternalId());
    }
  }

  @NotNull
  private Repository getCachedProject(@NotNull User user) throws IOException {
    try {
      if (user.isAnonymous())
        return cache.get("");

      final String key = user.getUsername();
      if (key.isEmpty()) {
        throw new IllegalStateException("Found user without identifier: " + user);
      }
      return cache.get(key);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw new IllegalStateException(e);
    }
  }
}
