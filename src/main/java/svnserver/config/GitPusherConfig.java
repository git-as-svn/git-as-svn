/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.jetbrains.annotations.NotNull;
import svnserver.context.SharedContext;
import svnserver.repository.git.push.GitPusher;

/**
 * Repository push configuration.
 *
 * @author a.navrotskiy
 */
public interface GitPusherConfig {
  @NotNull
  GitPusher create();
}
