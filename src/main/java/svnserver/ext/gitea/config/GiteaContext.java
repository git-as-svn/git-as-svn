/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.config;

import io.gitea.ApiClient;
import io.gitea.auth.ApiKeyAuth;

import org.jetbrains.annotations.NotNull;
import svnserver.context.Shared;
import svnserver.context.SharedContext;

/**
 * Gitea context.
 *
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
public final class GiteaContext implements Shared {
  @NotNull
  private final GiteaConfig config;

  GiteaContext(@NotNull GiteaConfig config) {
    this.config = config;
  }

  @NotNull
  public static GiteaContext sure(@NotNull SharedContext context) {
    return context.sure(GiteaContext.class);
  }

  @NotNull
  public ApiClient connect(@NotNull String username, @NotNull String password) {
    ApiClient apiClient = new ApiClient();
    apiClient.setBasePath(config.getUrl());
    apiClient.setUsername(username);
    apiClient.setPassword(password);
    return apiClient;
  }

  @NotNull
  public static ApiClient connect(@NotNull String giteaUrl, @NotNull GiteaToken token) {
    ApiClient apiClient = new ApiClient();
    apiClient.setBasePath(giteaUrl);
    apiClient.setApiKey(token.getValue());
    apiClient.setApiKeyPrefix("token");
    return apiClient;
  }

  @NotNull
  public ApiClient connect() {
    return connect(getGiteaUrl(), config.getToken());
  }

  @NotNull
  public ApiClient connect(@NotNull String username) {
    ApiClient apiClient = connect();
    ApiKeyAuth sudoParam = (ApiKeyAuth) apiClient.getAuthentication("SudoParam");
    sudoParam.setApiKey(username);
    return apiClient;
  }

  @NotNull
  public String getGiteaUrl() {
    return config.getUrl();
  }
}
