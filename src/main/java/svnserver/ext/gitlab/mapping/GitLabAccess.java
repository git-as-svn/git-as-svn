/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.gitlfs.common.JsonHelper;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.ext.gitlab.config.GitLabContext;
import svnserver.repository.VcsAccess;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Access control by GitLab server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
final class GitLabAccess implements VcsAccess {
  @NotNull
  private final LoadingCache<String, GitlabProject> cache;
  @NotNull
  private final Map<String, String> environment;

  GitLabAccess(@NotNull LocalContext local, @NotNull GitLabMappingConfig config, @NotNull GitlabProject gitlabProject, @NotNull Path relativeRepoPath) throws JsonProcessingException {
    this.environment = new HashMap<>();
    final String glRepository = String.format("project-%s", gitlabProject.getId());
    this.environment.put("GL_REPOSITORY", glRepository);

    final Map<String, String> gitalyRepo = new HashMap<>();
    gitalyRepo.put("storageName", "default");
    gitalyRepo.put("glRepository", glRepository);
    gitalyRepo.put("relativePath", relativeRepoPath.toString());
    gitalyRepo.put("glProjectPath", gitlabProject.getPathWithNamespace());
    this.environment.put("GITALY_REPO", JsonHelper.mapper.writeValueAsString(gitalyRepo));

    final GitLabContext context = GitLabContext.sure(local.getShared());

    this.cache = CacheBuilder.newBuilder()
        .maximumSize(config.getCacheMaximumSize())
        .expireAfterWrite(config.getCacheTimeSec(), TimeUnit.SECONDS)
        .build(
            new CacheLoader<String, GitlabProject>() {
              @Override
              public GitlabProject load(@NotNull String userId) throws Exception {
                if (userId.isEmpty())
                  return GitlabAPI.connect(context.getGitLabUrl(), null).getProject(gitlabProject.getId());

                final GitlabAPI api = context.connect();
                final String tailUrl = GitlabProject.URL + "/" + gitlabProject.getId() + "?sudo=" + userId;
                return api.retrieve().to(tailUrl, GitlabProject.class);
              }
            }
        );
  }

  @Override
  public boolean canRead(@NotNull User user, @NotNull String branch, @NotNull String path) throws IOException {
    try {
      getProjectViaSudo(user);
      return true;
    } catch (FileNotFoundException ignored) {
      return false;
    }
  }

  @Override
  public boolean canWrite(@NotNull User user, @NotNull String branch, @NotNull String path) throws IOException {
    if (user.isAnonymous())
      return false;

    try {
      final GitlabProject project = getProjectViaSudo(user);
      if (isProjectOwner(project, user))
        return true;

      final GitlabPermission permissions = project.getPermissions();
      if (permissions == null)
        return false;

      return hasAccess(permissions.getProjectAccess(), GitlabAccessLevel.Developer)
          || hasAccess(permissions.getProjectGroupAccess(), GitlabAccessLevel.Developer);
    } catch (FileNotFoundException ignored) {
      return false;
    }
  }

  @Override
  public void updateEnvironment(@NotNull Map<String, String> environment) {
    environment.putAll(this.environment);
  }

  private boolean isProjectOwner(@NotNull GitlabProject project, @NotNull User user) {
    if (user.isAnonymous()) {
      return false;
    }
    GitlabUser owner = project.getOwner();
    //noinspection SimplifiableIfStatement
    if (owner == null) {
      return false;
    }
    return owner.getId().toString().equals(user.getExternalId())
        || owner.getName().equals(user.getUsername());
  }

  private boolean hasAccess(@Nullable GitlabProjectAccessLevel access, @NotNull GitlabAccessLevel level) {
    if (access == null) return false;
    GitlabAccessLevel accessLevel = access.getAccessLevel();
    return accessLevel != null && (accessLevel.accessValue >= level.accessValue);
  }

  @NotNull
  private GitlabProject getProjectViaSudo(@NotNull User user) throws IOException {
    try {
      if (user.isAnonymous())
        return cache.get("");

      final String key = user.getExternalId() != null ? user.getExternalId() : user.getUsername();
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
