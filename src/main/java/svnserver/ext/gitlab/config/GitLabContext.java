/**
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
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.GitlabAPIException;
import org.gitlab.api.TokenType;
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
public final class GitLabContext implements Shared {
  @NotNull
  private static final HttpTransport transport = new NetHttpTransport();
  @NotNull
  private static final JsonFactory jsonFactory = new JacksonFactory();
  @NotNull
  private final GitLabConfig config;

  GitLabContext(@NotNull GitLabConfig config) {
    this.config = config;
  }

  @NotNull
  public static GitLabContext sure(@NotNull SharedContext context) throws IOException, SVNException {
    return context.sure(GitLabContext.class);
  }

  @NotNull
  public GitlabSession connect(@NotNull String username, @NotNull String password) throws IOException {
    String token = obtainAccessToken(config.getUrl(), username, password);
    final GitlabAPI api = GitlabAPI.connect(config.getUrl(), token, TokenType.ACCESS_TOKEN);
    return api.getCurrentSession();
  }

  @NotNull
  public static String obtainAccessToken(@NotNull String gitlabUrl, @NotNull String username, @NotNull String password) throws IOException {
    try {
      final TokenResponse oauthResponse = new PasswordTokenRequest(transport, jsonFactory, new OAuthGetAccessToken(gitlabUrl + "/oauth/token"), username, password).execute();
      return oauthResponse.getAccessToken();
    } catch (TokenResponseException e) {
      throw new GitlabAPIException(e.getMessage(), e.getStatusCode(), e);
    }
  }

  @NotNull
  public GitlabAPI connect() {
    return GitlabAPI.connect(config.getUrl(), config.getToken(), config.getTokenType());
  }

  @NotNull
  public String getHookUrl() {
    return config.getHookUrl();
  }
}
