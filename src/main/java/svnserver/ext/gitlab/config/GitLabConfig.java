/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.config;

import org.gitlab.api.TokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.gitlfs.client.auth.AuthProvider;
import ru.bozaro.gitlfs.client.auth.BasicAuthProvider;
import svnserver.auth.User;
import svnserver.config.SharedConfig;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;
import svnserver.ext.gitlfs.storage.BasicAuthHttpLfsStorage;
import svnserver.ext.gitlfs.storage.LfsReader;
import svnserver.ext.gitlfs.storage.LfsStorage;
import svnserver.ext.gitlfs.storage.LfsStorageFactory;

import java.io.IOException;
import java.net.URI;

/**
 * Gitlab access settings.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("gitlab")
public final class GitLabConfig implements SharedConfig {
  @NotNull
  private String url;
  @NotNull
  private String token;
  @NotNull
  private TokenType tokenType;
  @NotNull
  private String hookPath = "_hooks/gitlab";
  @Nullable
  private LfsMode lfsMode = HttpLfsMode.instance;
  @NotNull
  private String gitalyBinDir = "/opt/gitlab/embedded/bin";
  @NotNull
  private String gitalySocket = "/var/opt/gitlab/gitaly/gitaly.socket";
  @NotNull
  private String gitalyToken = "secret token";

  public GitLabConfig() {
    this("http://localhost/", TokenType.PRIVATE_TOKEN, "");
  }

  private GitLabConfig(@NotNull String url, @NotNull TokenType tokenType, @NotNull String token) {
    this.url = url;
    this.token = token;
    this.tokenType = tokenType;
  }

  public GitLabConfig(@NotNull String url, @NotNull GitLabToken token) {
    this(url, token.getType(), token.getValue());
  }

  @NotNull
  public String getUrl() {
    return url;
  }

  @NotNull
  String getHookPath() {
    return hookPath;
  }

  @NotNull
  public String getGitalyBinDir() {
    return gitalyBinDir;
  }

  @NotNull
  public String getGitalyToken() {
    return gitalyToken;
  }

  @NotNull
  public String getGitalySocket() {
    return gitalySocket;
  }

  @Override
  public void create(@NotNull SharedContext context) {
    final GitLabContext gitLabContext = new GitLabContext(this);
    context.add(GitLabContext.class, gitLabContext);

    if (lfsMode != null) {
      context.add(LfsStorageFactory.class, localContext -> createLfsStorage(
          url,
          localContext.getName(),
          "UNUSED", getToken().getValue(),
          lfsMode.readerFactory(localContext)
      ));
    }
  }

  @NotNull
  public static LfsStorage createLfsStorage(
      @NotNull String gitLabUrl,
      @NotNull String repositoryName,
      @NotNull String username,
      @NotNull String password,
      @Nullable LfsReaderFactory readerFactory) {
    return new BasicAuthHttpLfsStorage(gitLabUrl, repositoryName, username, password) {
      @Override
      public @Nullable LfsReader getReader(@NotNull String oid, long size) throws IOException {
        if (readerFactory != null)
          return readerFactory.createReader(oid);
        else
          return super.getReader(oid, size);
      }

      @NotNull
      @Override
      protected AuthProvider authProvider(@NotNull User user, @NotNull URI baseURI) {
        final User.LfsCredentials lfsCredentials = user.getLfsCredentials();
        if (lfsCredentials == null)
          return super.authProvider(user, baseURI);

        return new BasicAuthProvider(baseURI, lfsCredentials.username, lfsCredentials.password);
      }
    };
  }

  @NotNull
  public GitLabToken getToken() {
    return new GitLabToken(tokenType, token);
  }
}
