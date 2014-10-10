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
import svnserver.repository.VcsRepository;

import java.io.IOException;

/**
 * Lock manager factory.
 *
 * @author a.navrotskiy
 */
public interface LockManagerFactory {
  @NotNull
  <T> T wrapLockRead(@NotNull VcsRepository repository, @NotNull LockWorker<T, LockManagerRead> work) throws IOException, SVNException;

  @NotNull
  <T> T wrapLockWrite(@NotNull VcsRepository repository, @NotNull LockWorker<T, LockManagerWrite> work) throws IOException, SVNException;
}
