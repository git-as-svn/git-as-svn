/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.push;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.User;

import java.io.IOException;
import java.util.Map;

/**
 * Interface for pushing new commit to the repository.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface GitPusher {
  /**
   * Push commits. Only fast forward support
   *
   * @param repository Repository
   * @param commitId   Commit ID
   * @param branch     Branch name
   * @param userInfo   User info
   * @return Return true if data is pushed successfully. And false on non fast-forward push failure.
   * @throws SVNException
   * @throws IOException
   */
  boolean push(@NotNull Repository repository, @NotNull ObjectId commitId, @NotNull String branch, @NotNull User userInfo) throws SVNException, IOException;

  default void updateEnvironment(Map<String, String> environment, User userInfo) {
    environment.put("GAS_EMAIL", userInfo.getEmail());
    environment.put("GAS_NAME", userInfo.getRealName());
    environment.put("GAS_LOGIN", userInfo.getUserName());
  }
}
