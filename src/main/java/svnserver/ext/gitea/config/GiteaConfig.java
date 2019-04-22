/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.config;

import org.jetbrains.annotations.NotNull;
import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;

import java.io.IOException;

/**
 * Gitea access settings.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
@ConfigType("gitea")
public final class GiteaConfig implements SharedConfig {
  @NotNull
  private String url;
  @NotNull
  private String token;

  public GiteaConfig() {
    this("http://localhost/", "");
  }

  public GiteaConfig(@NotNull String url, @NotNull GiteaToken token) {
    this(url, token.getValue());
  }

  private GiteaConfig(@NotNull String url, @NotNull String token) {
    this.url = url;
    this.token = token;
  }

  @NotNull
  public String getUrl() {
    return url;
  }

  @NotNull
  public GiteaToken getToken() {
    return new GiteaToken(token);
  }

  @Override
  public void create(@NotNull SharedContext context) throws IOException {
    context.add(GiteaContext.class, new GiteaContext(this));
  }
}
