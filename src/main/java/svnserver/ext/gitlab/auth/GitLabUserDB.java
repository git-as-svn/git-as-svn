/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.auth;

import org.gitlab.api.GitlabAPIException;
import org.gitlab.api.models.GitlabSession;
import org.gitlab.api.models.GitlabUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import svnserver.Loggers;
import svnserver.UserType;
import svnserver.auth.Authenticator;
import svnserver.auth.PlainAuthenticator;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.context.SharedContext;
import svnserver.ext.gitlab.config.GitLabContext;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * GitLab user authentiation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitLabUserDB implements UserDB {
  @NotNull
  private static final Logger log = Loggers.gitlab;
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

  @Override
  public User check(@NotNull String username, @NotNull String password) {
    try {
      final GitlabSession session = context.connect(username, password);
      return createUser(session);
    } catch (GitlabAPIException e) {
      if (e.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
        return null;
      }
      log.warn("User password check error: " + username, e);
      return null;
    } catch (IOException e) {
      log.warn("User password check error: " + username, e);
      return null;
    }
  }

  @NotNull
  private User createUser(@NotNull GitlabUser user) {
    return User.create(user.getUsername(), user.getName(), user.getEmail(), user.getId().toString(), UserType.GitLab);
  }

  @Nullable
  @Override
  public User lookupByUserName(@NotNull String username) {
    try {
      return createUser(context.connect().getUserViaSudo(username));
    } catch (FileNotFoundException e) {
      return null;
    } catch (IOException e) {
      log.warn("User lookup by name error: " + username, e);
      return null;
    }
  }

  @Nullable
  @Override
  public User lookupByExternal(@NotNull String external) {
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
}
