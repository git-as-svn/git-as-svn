/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.auth;

import org.gitlab.api.models.GitlabSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.*;
import svnserver.context.SharedContext;
import svnserver.ext.gitlab.config.GitLabContext;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * GitLab user authentiation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitLabUserDB implements UserDB, PasswordChecker {
  @NotNull
  private final Collection<Authenticator> authenticators = Collections.singleton(new PlainAuthenticator(this));
  @NotNull
  private final GitLabContext context;

  public GitLabUserDB(@NotNull SharedContext context) {
    this.context = context.sure(GitLabContext.class);
  }

  @NotNull
  @Override
  public Collection<Authenticator> authenticators() {
    return authenticators;
  }

  @Nullable
  @Override
  public User check(@NotNull String username, @NotNull String password) throws SVNException, IOException {
    try {
      final GitlabSession session = context.connect(username, password);
      return new GitLabUser(session);
    } catch (IOException e) {
      return null;
    }
  }

  private static class GitLabUser extends User {
    private final GitlabSession session;

    public GitLabUser(GitlabSession session) {
      super(session.getUsername(), session.getName(), session.getEmail());
      this.session = session;
    }

    @Override
    public void updateEnvironment(@NotNull Map<String, String> env) {
      super.updateEnvironment(env);
      env.put("GL_ID", "user-" + (int) session.getId());
    }
  }
}
