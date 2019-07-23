/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.User;
import svnserver.context.Local;

import java.io.IOException;
import java.util.Map;

/**
 * Repository access checker.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public interface VcsAccess extends Local {

  default void checkRead(@NotNull User user, @NotNull String branch, @NotNull String path) throws IOException, SVNException {
    if (!canRead(user, branch, path))
      throw new SVNException(SVNErrorMessage.create(user.isAnonymous() ? SVNErrorCode.RA_NOT_AUTHORIZED : SVNErrorCode.AUTHZ_UNREADABLE));
  }

  boolean canRead(@NotNull User user, @NotNull String branch, @NotNull String path) throws IOException;

  default void checkWrite(@NotNull User user, @NotNull String branch, @NotNull String path) throws IOException, SVNException {
    if (user.isAnonymous())
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED));

    if (!canWrite(user, branch, path))
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHZ_UNWRITABLE));
  }

  boolean canWrite(@NotNull User user, @NotNull String branch, @NotNull String path) throws IOException;

  default void updateEnvironment(@NotNull Map<String, String> environment) {
  }
}
