/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.auth;

import org.gitlab.api.GitlabAPIException;
import org.gitlab.api.models.GitlabUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.*;
import svnserver.context.SharedContext;
import svnserver.ext.gitlab.config.GitLabContext;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * GitLab user authentiation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitLabUserDB implements UserDB, UserLookupVisitor {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitLabUserDB.class);
  @NotNull
  private static final String PREFIX_USER = "user-";
  @NotNull
  private static final String PREFIX_KEY = "key-";

  @NotNull
  private final Collection<Authenticator> authenticators = Collections.singleton(new PlainAuthenticator(this));
  @NotNull
  private final GitLabContext context;

  GitLabUserDB(@NotNull SharedContext context) {
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
      return createUser(context.connect(userName, password));
    } catch (GitlabAPIException e) {
      if (e.getResponseCode() == 401) {
        return null;
      }
      log.warn("User password check error: " + userName, e);
      return null;
    } catch (IOException e) {
      log.warn("User password check error: " + userName, e);
      return null;
    }
  }

  @Nullable
  @Override
  public User lookupByUserName(@NotNull String userName) throws SVNException, IOException {
    try {
      return createUser(context.connect().getUserViaSudo(userName));
    } catch (FileNotFoundException e) {
      return null;
    } catch (IOException e) {
      log.warn("User lookup by name error: " + userName, e);
      return null;
    }
  }

  @Nullable
  @Override
  public User lookupByExternal(@NotNull String external) throws SVNException, IOException {
    final Integer userId = removePrefix(external, PREFIX_USER);
    if (userId != null) {
      try {
        return createUser(context.connect().getUser(userId));
      } catch (FileNotFoundException e) {
        return null;
      } catch (IOException e) {
        log.warn("User lookup by userId error: " + external, e);
        return null;
      }
    }
    final Integer keyId = removePrefix(external, PREFIX_KEY);
    if (keyId != null) {
      try {
        return createUser(context.connect().getSSHKey(keyId).getUser());
      } catch (FileNotFoundException e) {
        return null;
      } catch (IOException e) {
        log.warn("User lookup by SSH key error: " + external, e);
        return null;
      }
    }
    return null;
  }

  @Override
  public void updateEnvironment(@NotNull Map<String, String> env, @NotNull User userInfo) {
    final String externalId = userInfo.getExternalId();
    if (externalId != null) {
      env.put("GL_ID", PREFIX_USER + externalId);
    }
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

  private User createUser(@NotNull GitlabUser user) {
    return User.create(user.getUsername(), user.getName(), user.getEmail(), user.getId().toString());
  }
}
