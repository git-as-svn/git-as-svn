/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.config;

import org.jetbrains.annotations.NotNull;
import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;

import java.io.IOException;

/**
 * Gitlab access settings.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("gitlab")
public class GitLabConfig implements SharedConfig {
  @NotNull
  private String url;
  @NotNull
  private String token;
  @NotNull
  private String hookUrl = "http://localhost:8123/hooks/gitlab";

  public GitLabConfig() {
    this("http://localhost/", "");
  }

  public GitLabConfig(@NotNull String url, @NotNull String token) {
    this.url = url;
    this.token = token;
  }

  @NotNull
  public String getUrl() {
    return url;
  }

  @NotNull
  public String getToken() {
    return token;
  }

  @NotNull String getHookUrl() {
    return hookUrl;
  }

  @Override
  public void create(@NotNull SharedContext context) throws IOException {
    context.add(GitLabContext.class, new GitLabContext(this));
  }
}
