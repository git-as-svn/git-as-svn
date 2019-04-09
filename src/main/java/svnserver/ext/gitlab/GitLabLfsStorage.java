/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab;

import org.jetbrains.annotations.NotNull;
import ru.bozaro.gitlfs.client.Client;
import ru.bozaro.gitlfs.client.auth.BasicAuthProvider;
import svnserver.auth.User;
import svnserver.ext.gitlab.config.GitLabToken;
import svnserver.ext.gitlfs.storage.network.LfsHttpStorage;

import java.net.URI;

/**
 * HTTP remote storage for LFS files.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitLabLfsStorage extends LfsHttpStorage {
  @NotNull
  private static final String LFS_PATH = ".git/info/lfs";
  @NotNull
  private final Client client;

  public GitLabLfsStorage(@NotNull String repositoryUrl, @NotNull GitLabToken gitLabToken) {
    this(repositoryUrl, "UNUSED", gitLabToken.getValue());
  }

  public GitLabLfsStorage(@NotNull String repositoryUrl, @NotNull String username, @NotNull String password) {
    client = new Client(new BasicAuthProvider(URI.create(repositoryUrl + LFS_PATH), username, password));
  }

  @NotNull
  protected Client lfsClient(@NotNull User user) {
    return client;
  }

  public void invalidate(@NotNull User user) {
  }
}
