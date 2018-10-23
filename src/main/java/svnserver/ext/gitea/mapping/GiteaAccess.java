/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.mapping;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;

import io.gitea.ApiClient;
import io.gitea.ApiException;
import io.gitea.api.RepositoryApi;
import io.gitea.model.Permission;
import io.gitea.model.Repository;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.ext.gitea.config.GiteaContext;
import svnserver.repository.VcsAccess;

/**
 * Access control by Gitea server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
final class GiteaAccess implements VcsAccess {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GiteaAccess.class);
  @NotNull
  private final LoadingCache<String, Repository> cache;
  @NotNull
  private final HashMap<String, String> environment;

  GiteaAccess(@NotNull LocalContext local, @NotNull GiteaMappingConfig config, Repository repository) {
    final long projectId = repository.getId();
    this.environment = new HashMap<String, String>();
    this.environment.put("GITEA_REPO_ID", "" + repository.getId());
    this.environment.put("GITEA_REPO_IS_WIKI", "false");
    this.environment.put("GITEA_REPO_NAME", repository.getName());
    this.environment.put("GITEA_REPO_USER", repository.getOwner().getLogin());

    final GiteaContext context = GiteaContext.sure(local.getShared());

    this.cache = CacheBuilder.newBuilder().maximumSize(config.getCacheMaximumSize())
        .expireAfterWrite(config.getCacheTimeSec(), TimeUnit.SECONDS).build(new CacheLoader<String, Repository>() {
          @Override
          public Repository load(@NotNull String userName) throws Exception {
            if (userName.isEmpty()) {
              try {
                final ApiClient apiClient = context.connect();
                final RepositoryApi repositoryApi = new RepositoryApi(apiClient);
                final Repository repository = repositoryApi.repoGetByID((int) projectId);
                if (!repository.isPrivate()) {
                  // Munge the permissions
                  repository.getPermissions().setAdmin(false);
                  repository.getPermissions().setPush(false);
                  return repository;
                }
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
              final ApiClient apiClient = context.connect(userName);
              final RepositoryApi repositoryApi = new RepositoryApi(apiClient);

              final Repository repository = repositoryApi.repoGetByID((int) projectId);
              return repository;
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
  public void updateEnvironment(@NotNull Map<String, String> environment) {
    environment.putAll(this.environment);
  }

  @Override
  public void checkRead(@NotNull User user, @Nullable String path) throws SVNException, IOException {
    try {
      Repository repository = getCachedProject(user);
      if (repository.isPrivate()) {
        Permission permission = repository.getPermissions();
        if (!(permission.isAdmin() || permission.isPull())) {
          throw new SVNException(
              SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "You're not authorized to read this repository"));
        }
      }
    } catch (FileNotFoundException ignored) {
      throw new SVNException(
          SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "You're not authorized to read this repository"));
    }
  }

  @Override
  public void checkWrite(@NotNull User user, @Nullable String path) throws SVNException, IOException {
    if (user.isAnonymous()) {
      throw new SVNException(
          SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Anonymous user has no repository write access"));
    }
    try {
      final Repository repository = getCachedProject(user);
      if (repository != null) {
        final Permission permission = repository.getPermissions();
        if (permission.isAdmin() || permission.isPush()) {
          return;
        }
      }
      throw new SVNException(
          SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "You're not authorized to write this repository"));
    } catch (FileNotFoundException ignored) {
      throw new SVNException(
          SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "You're not authorized to read this repository"));
    }
  }

  @NotNull
  private Repository getCachedProject(@NotNull User user) throws IOException {
    try {
      if (user.isAnonymous())
        return cache.get("");

      final String key = user.getUserName();
      if (key.isEmpty()) {
        throw new IllegalStateException("Found user without identificator: " + user);
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
