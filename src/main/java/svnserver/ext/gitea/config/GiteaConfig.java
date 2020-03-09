/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.config;

import org.jetbrains.annotations.NotNull;
import ru.bozaro.gitlfs.client.auth.AuthProvider;
import ru.bozaro.gitlfs.client.auth.BasicAuthProvider;
import svnserver.auth.User;
import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.storage.BasicAuthHttpLfsStorage;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsStorageFactory;

import java.net.URI;

/**
 * Gitea access settings.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
@ConfigType("gitea")
public final class GiteaConfig implements SharedConfig {
  @NotNull
  private String url;
  @NotNull
  private String token;
  private boolean lfs = true;

  public GiteaConfig() {
    this("http://localhost/", "");
  }

  private GiteaConfig(@NotNull String url, @NotNull String token) {
    this.url = url;
    this.token = token;
  }

  public GiteaConfig(@NotNull String url, @NotNull GiteaToken token) {
    this(url, token.getValue());
  }

  @NotNull
  public String getUrl() {
    return url;
  }

  @Override
  public void create(@NotNull SharedContext context) {
    final GiteaContext giteaContext = new GiteaContext(this);
    context.add(GiteaContext.class, giteaContext);

    if (lfs) {
      context.add(LfsStorageFactory.class, localContext -> createLfsStorage(url, localContext.getName(), getToken()));
    }
  }

  @NotNull
  public static LfsStorage createLfsStorage(@NotNull String giteaUrl, @NotNull String repositoryName, @NotNull GiteaToken token) {
    return new BasicAuthHttpLfsStorage(giteaUrl, repositoryName, token.getValue(), "x-oauth-basic") {
      @Override
      protected @NotNull AuthProvider authProvider(@NotNull User user, @NotNull URI baseURI) {
        final User.LfsCredentials lfsCredentials = user.getLfsCredentials();
        if (lfsCredentials == null)
          return super.authProvider(user, baseURI);

        return new BasicAuthProvider(baseURI, lfsCredentials.username, lfsCredentials.password);
      }
    };
  }

  @NotNull
  public GiteaToken getToken() {
    return new GiteaToken(token);
  }
}
