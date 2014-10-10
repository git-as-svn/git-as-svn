/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.locks;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;

/**
 * Worker for execute some work with lock information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@FunctionalInterface
public interface LockWorker<T, M extends LockManagerRead> {
  @NotNull
  T exec(@NotNull M lockManager) throws SVNException, IOException;
}
