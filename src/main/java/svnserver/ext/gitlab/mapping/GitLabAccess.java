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
import org.gitlab.api.models.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.ext.gitlab.config.GitLabContext;
import svnserver.repository.VcsAccess;

import java.io.FileNotFoundException;
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
                String tailUrl = GitlabProject.URL + "/" + projectId;
                if (!userId.isEmpty()) {
                  tailUrl += "?sudo=" + userId;
                }
                return api.retrieve().to(tailUrl, GitlabProject.class);
              }
            }
        );
  }

  @Override
  public void checkRead(@NotNull User user, @Nullable String path) throws SVNException, IOException {
    try {
      if (user.isAnonymous()) {
        if (!getProjectAsAdmin().isPublic()) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "This project has not public access"));
        }
      } else {
        getProjectViaSudo(user);
      }
    } catch (FileNotFoundException ignored) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "You're not authorized to read this project"));
    }
  }

  @Override
  public void checkWrite(@NotNull User user, @Nullable String path) throws SVNException, IOException {
    if (user.isAnonymous()) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Anonymous user has no project write access"));
    }
    try {
      final GitlabProject project = getProjectViaSudo(user);
      if (isProjectOwner(project, user)) {
        return;
      }

      final GitlabPermission permissions = project.getPermissions();
      if (permissions != null) {
        if (hasAccess(permissions.getProjectAccess(), GitlabAccessLevel.Developer) ||
            hasAccess(permissions.getProjectGroupAccess(), GitlabAccessLevel.Developer)) {
          return;
        }
      }
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "You're not authorized to write this project"));
    } catch (FileNotFoundException ignored) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "You're not authorized to read this project"));
    }
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
        || owner.getName().equals(user.getUserName());
  }

  private boolean hasAccess(@Nullable GitlabProjectAccessLevel access, @NotNull GitlabAccessLevel level) {
    if (access == null) return false;
    GitlabAccessLevel accessLevel = access.getAccessLevel();
    return accessLevel != null && (accessLevel.accessValue >= level.accessValue);
  }

  @NotNull
  private GitlabProject getProjectAsAdmin() throws IOException {
    try {
      return cache.get("");
    } catch (ExecutionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw new IllegalStateException(e);
    }
  }

  @NotNull
  private GitlabProject getProjectViaSudo(@NotNull User user) throws IOException {
    try {
      final String key = user.getExternalId() != null ? user.getExternalId() : user.getUserName();
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
