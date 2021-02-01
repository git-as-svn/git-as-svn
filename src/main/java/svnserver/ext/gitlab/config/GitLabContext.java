/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.config;

import com.google.api.client.auth.oauth.OAuthGetAccessToken;
import com.google.api.client.auth.oauth2.PasswordTokenRequest;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.GitlabAPIException;
import org.gitlab.api.TokenType;
import org.gitlab.api.models.GitlabSession;
import org.jetbrains.annotations.NotNull;
import svnserver.context.Shared;
import svnserver.context.SharedContext;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * GitLab context.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitLabContext implements Shared {
  @NotNull
  private static final HttpTransport transport = new NetHttpTransport();
  @NotNull
  private final GitLabConfig config;

  GitLabContext(@NotNull GitLabConfig config) {
    this.config = config;
  }

  @NotNull
  public static GitLabContext sure(@NotNull SharedContext context) {
    return context.sure(GitLabContext.class);
  }

  @NotNull
  public GitlabSession connect(@NotNull String username, @NotNull String password) throws IOException {
    final GitLabToken token = obtainAccessToken(getGitLabUrl(), username, password, false);
    final GitlabAPI api = connect(getGitLabUrl(), token);
    return api.getCurrentSession();
  }

  @NotNull
  public static GitLabToken obtainAccessToken(@NotNull String gitlabUrl, @NotNull String username, @NotNull String password, boolean sudoScope) throws IOException {
    try {
      final OAuthGetAccessToken tokenServerUrl = new OAuthGetAccessToken(gitlabUrl + "/oauth/token?scope=api" + (sudoScope ? "%20sudo" : ""));
      final TokenResponse oauthResponse = new PasswordTokenRequest(transport, JacksonFactory.getDefaultInstance(), tokenServerUrl, username, password).execute();
      return new GitLabToken(TokenType.ACCESS_TOKEN, oauthResponse.getAccessToken());
    } catch (TokenResponseException e) {
      if (sudoScope && e.getStatusCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
        // Fallback for pre-10.2 gitlab versions
        final GitlabSession session = GitlabAPI.connect(gitlabUrl, username, password);
        return new GitLabToken(TokenType.PRIVATE_TOKEN, session.getPrivateToken());
      } else {
        throw new GitlabAPIException(e.getMessage(), e.getStatusCode(), e);
      }
    }
  }

  @NotNull
  public String getGitLabUrl() {
    return config.getUrl();
  }

  @NotNull
  public static GitlabAPI connect(@NotNull String gitlabUrl, @NotNull GitLabToken token) {
    return GitlabAPI.connect(gitlabUrl, token.getValue(), token.getType());
  }

  @NotNull
  public GitlabAPI connect() {
    return connect(getGitLabUrl(), config.getToken());
  }

  @NotNull
  public GitLabToken getToken() {
    return config.getToken();
  }

  @NotNull
  public String getHookPath() {
    return config.getHookPath();
  }

  @NotNull
  public GitLabConfig getConfig() {
    return config;
  }
}
