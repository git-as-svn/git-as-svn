/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.config;

import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabSession;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.context.Shared;
import svnserver.context.SharedContext;

import java.io.IOException;

/**
 * GitLab context.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitLabContext implements Shared {
  @NotNull
  private final String url;
  @NotNull
  private final String token;

  public GitLabContext(@NotNull String url, @NotNull String token) {
    this.url = url;
    this.token = token;
  }

  @NotNull
  public GitlabAPI connect() {
    return GitlabAPI.connect(url, token);
  }

  @NotNull
  public GitlabSession connect(@NotNull String username, @NotNull String password) throws IOException {
    return GitlabAPI.connect(url, username, password);
  }

  @NotNull
  public static GitLabContext sure(@NotNull SharedContext context) throws IOException, SVNException {
    return context.sure(GitLabContext.class);
  }
}
