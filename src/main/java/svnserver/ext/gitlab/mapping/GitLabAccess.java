/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabAccessLevel;
import org.gitlab.api.models.GitlabPermission;
import org.gitlab.api.models.GitlabProject;
import org.gitlab.api.models.GitlabProjectAccessLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.ext.gitlab.config.GitLabContext;
import svnserver.repository.VcsAccess;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Access control by GitLab server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitLabAccess implements VcsAccess {
  @NotNull
  private final LoadingCache<String, GitlabProject> cache;

  public GitLabAccess(@NotNull LocalContext local, @NotNull GitLabMappingConfig config, int projectId) {
    this.cache = CacheBuilder.newBuilder()
        .maximumSize(config.getCacheMaximumSize())
        .expireAfterWrite(config.getCacheTimeSec(), TimeUnit.SECONDS)
        .build(
            new CacheLoader<String, GitlabProject>() {
              @Override
              public GitlabProject load(@NotNull String userId) throws Exception {
                final GitlabAPI api = GitLabContext.sure(local.getShared()).connect();
                final String tailUrl = GitlabProject.URL + "/" + projectId + "?sudo=" + userId;
                return api.retrieve().to(tailUrl, GitlabProject.class);
              }
            }
        );
  }

  @Override
  public void checkRead(@NotNull User user, @Nullable String path) throws SVNException, IOException {
    check(user, GitlabAccessLevel.Reporter);
  }

  @Override
  public void checkWrite(@NotNull User user, @Nullable String path) throws SVNException, IOException {
    check(user, GitlabAccessLevel.Developer);
  }

  private void check(@NotNull User user, @NotNull GitlabAccessLevel accessLevel) throws IOException, SVNException {
    final GitlabAccessLevel userLevel = getProjectAccess(user);
    if (userLevel == null || userLevel.accessValue <= accessLevel.accessValue) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "You're not authorized to access this project"));
    }
  }

  @Nullable
  private GitlabAccessLevel getProjectAccess(@NotNull User user) throws IOException {
    final GitlabPermission permissions = getProjectViaSudo(user).getPermissions();
    if (permissions == null) return null;
    final GitlabProjectAccessLevel projectAccess = permissions.getProjectAccess();
    if (projectAccess == null) return null;
    return projectAccess.getAccessLevel();
  }

  @NotNull
  private GitlabProject getProjectViaSudo(@NotNull User user) throws IOException {
    try {
      return cache.get(user.getExternalId() != null ? user.getExternalId() : user.getUserName());
    } catch (ExecutionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw new IllegalStateException(e);
    }
  }
}
