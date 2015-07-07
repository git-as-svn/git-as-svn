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
import svnserver.repository.locks.LockManagerWrite;

import java.io.IOException;
import java.util.Map;

/**
 * Interface for writing data to repository in commit.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface VcsWriter {

  /**
   * Create new file in repository.
   *
   * @param parent Parent directory.
   * @param name   File name.
   * @return File updater.
   */
  @NotNull
  VcsDeltaConsumer createFile(@NotNull VcsEntry parent, @NotNull String name) throws IOException, SVNException;

  /**
   * Modification of the existing file.
   *
   * @param parent Parent directory.
   * @param name   File name.
   * @param file   File for modification.
   * @return File updater.
   */
  @NotNull
  VcsDeltaConsumer modifyFile(@NotNull VcsEntry parent, @NotNull String name, @NotNull VcsFile file) throws IOException, SVNException;

  /**
   * Create tree for commit.
   *
   * @return Commit build.
   * @throws IOException
   */
  @NotNull
  VcsCommitBuilder createCommitBuilder(@NotNull LockManagerWrite lockManager, @NotNull Map<String, String> locks) throws IOException, SVNException;
}
