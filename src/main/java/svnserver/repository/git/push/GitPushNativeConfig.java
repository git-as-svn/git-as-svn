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
 * Git push by native git client.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("push-native")
public class GitPushNativeConfig implements GitPusherConfig {
  @NotNull
  public static final GitPushNativeConfig instance = new GitPushNativeConfig();

  @NotNull
  @Override
  public GitPusher create() {
    return GitPushNative.instance;
  }
}
