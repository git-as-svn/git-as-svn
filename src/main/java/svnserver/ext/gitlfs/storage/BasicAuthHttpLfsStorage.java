/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage;

import org.apache.http.impl.client.CloseableHttpClient;
import org.eclipse.jgit.lib.Constants;
import org.jetbrains.annotations.NotNull;
import ru.bozaro.gitlfs.client.Client;
import ru.bozaro.gitlfs.client.auth.AuthProvider;
import ru.bozaro.gitlfs.client.auth.BasicAuthProvider;
import svnserver.auth.User;
import svnserver.ext.gitlfs.server.LfsServer;
import svnserver.ext.gitlfs.storage.network.LfsHttpStorage;

import java.net.URI;

/**
 * HTTP remote storage for LFS files.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class BasicAuthHttpLfsStorage extends LfsHttpStorage {
  @NotNull
  private final CloseableHttpClient httpClient = createHttpClient();
  @NotNull
  private final URI baseURI;
  @NotNull
  private final BasicAuthProvider fallbackAuthProvider;

  public BasicAuthHttpLfsStorage(@NotNull String baseUrl, @NotNull String repositoryName, @NotNull String username, @NotNull String password) {
    baseURI = buildAuthURI(baseUrl, repositoryName);
    fallbackAuthProvider = new BasicAuthProvider(baseURI, username, password);
  }

  @NotNull
  protected static URI buildAuthURI(@NotNull String baseUrl, @NotNull String repositoryName) {
    if (!baseUrl.endsWith("/"))
      baseUrl += "/";

    return URI.create(baseUrl + repositoryName + Constants.DOT_GIT_EXT + "/" + LfsServer.SERVLET_BASE);
  }

  @NotNull
  protected final Client lfsClient(@NotNull User user) {
    return new Client(authProvider(user, baseURI), httpClient);
  }

  @NotNull
  protected AuthProvider authProvider(@NotNull User user, @NotNull URI baseURI) {
    return fallbackAuthProvider;
  }
}
