/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.auth;

import org.gitlab.api.models.GitlabUser;
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
public class GitLabUserDB implements UserDB, UserLookupVisitor {
  @NotNull
  private final static String PREFIX_USER = "user-";
  @NotNull
  private final static String PREFIX_KEY = "key-";

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
  public User check(@NotNull String userName, @NotNull String password) throws SVNException, IOException {
    try {
      return new GitLabUser(context.connect(userName, password));
    } catch (IOException e) {
      return null;
    }
  }

  @Nullable
  @Override
  public User lookupByUserName(@NotNull String userName) throws SVNException, IOException {
    try {
      return new GitLabUser(context.connect().getUserViaSudo(userName));
    } catch (IOException e) {
      return null;
    }
  }

  @Nullable
  @Override
  public User lookupByExternal(@NotNull String external) throws SVNException, IOException {
    final Integer userId = removePrefix(external, PREFIX_USER);
    if (userId != null) {
      try {
        return new GitLabUser(context.connect().getUser(userId));
      } catch (IOException e) {
        return null;
      }
    }
    // todo: [#9591 (gitlabhq)](/gitlabhq/gitlabhq/pull/9591)
    /*
    final Integer keyId = removePrefix(external, PREFIX_KEY);
    if (keyId != null) {
      try {
        return new GitLabUser(context.connect().getUserByKey(keyId));
      } catch (IOException e) {
        return null;
      }
    }
    */
    return null;
  }

  @Nullable
  private Integer removePrefix(@NotNull String glId, @NotNull String prefix) {
    if (glId.startsWith(prefix)) {
      int result = 0;
      for (int i = prefix.length(); i < glId.length(); ++i) {
        final char c = glId.charAt(i);
        if (c < '0' || c > '9') return null;
        result = result * 10 + (c - '0');
      }
      return result;
    }
    return null;
  }

  private static class GitLabUser extends User {
    private final int id;

    public GitLabUser(GitlabUser user) {
      super(user.getUsername(), user.getName(), user.getEmail());
      this.id = user.getId();
    }

    @Override
    public void updateEnvironment(@NotNull Map<String, String> env) {
      super.updateEnvironment(env);
      env.put("GL_ID", PREFIX_USER + id);
    }
  }
}
