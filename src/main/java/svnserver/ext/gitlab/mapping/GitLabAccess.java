/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping;

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

/**
 * Access control by GitLab server.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitLabAccess implements VcsAccess {
  @NotNull
  private final LocalContext local;
  private final int projectId;

  public GitLabAccess(@NotNull LocalContext local, int projectId) {
    this.local = local;
    this.projectId = projectId;
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
    final GitlabAPI api = GitLabContext.sure(local.getShared()).connect();
    final GitlabAccessLevel userLevel = getProjectAccess(api, user);
    if (userLevel == null || userLevel.accessValue <= accessLevel.accessValue) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "You're not authorized to access this project"));
    }
  }

  @Nullable
  private GitlabAccessLevel getProjectAccess(@NotNull GitlabAPI api, @NotNull User user) throws IOException {
    final GitlabPermission permissions = getProjectViaSudo(api, user).getPermissions();
    if (permissions == null) return null;
    final GitlabProjectAccessLevel projectAccess = permissions.getProjectAccess();
    if (projectAccess == null) return null;
    return projectAccess.getAccessLevel();
  }

  @NotNull
  private GitlabProject getProjectViaSudo(@NotNull GitlabAPI api, @NotNull User user) throws IOException {
    final String userId = user.getExternalId() != null ? user.getExternalId() : user.getUserName();
    final String tailUrl = GitlabProject.URL + "/" + projectId + "?sudo=" + userId;
    return api.retrieve().to(tailUrl, GitlabProject.class);
  }
}
