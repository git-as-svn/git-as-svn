/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.push;

import org.jetbrains.annotations.NotNull;
import svnserver.config.GitPusherConfig;
import svnserver.config.serializer.ConfigType;

/**
 * Git push by embedded git client.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("push-embedded")
public class GitPushEmbeddedConfig implements GitPusherConfig {
  @NotNull
  public static final GitPushEmbeddedConfig instance = new GitPushEmbeddedConfig();
  @NotNull
  private String preReceive = "pre-commit";
  @NotNull
  private String postReceive = "post-commit";

  @NotNull
  public String getPreReceive() {
    return preReceive;
  }

  public void setPreReceive(@NotNull String preCommit) {
    this.preReceive = preCommit;
  }

  @NotNull
  public String getPostReceive() {
    return postReceive;
  }

  public void setPostReceivet(@NotNull String postReceive) {
    this.postReceive = postReceive;
  }

  @NotNull
  @Override
  public GitPusher create() {
    return new GitPushEmbedded(getPreReceive(), getPostReceive());
  }
}
