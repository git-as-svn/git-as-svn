/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.User;
import svnserver.context.Local;

/**
 * Repository access checker.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface VcsAccess extends Local {
  void check(@NotNull User user, @NotNull String path) throws SVNException;
}
