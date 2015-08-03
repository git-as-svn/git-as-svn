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
import svnserver.repository.locks.LockManagerRead;
import svnserver.repository.locks.LockManagerWrite;
import svnserver.repository.locks.LockWorker;

import java.io.IOException;

/**
 * Repository interface.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface VcsRepository extends AutoCloseable {
  /**
   * Repository identificator.
   *
   * @return Repository identificator.
   */
  @NotNull
  String getUuid();

  /**
   * Get latest revision number.
   *
   * @return Latest revision number.
   */
  @NotNull
  VcsRevision getLatestRevision() throws IOException;

  /**
   * Get revision by date.
   *
   * @param dateTime Date.
   * @return Revision number.
   * @throws IOException
   */
  @NotNull
  VcsRevision getRevisionByDate(long dateTime) throws IOException;

  /**
   * Update revision information.
   *
   * @throws IOException
   */
  void updateRevisions() throws IOException, SVNException;

  /**
   * Get revision info.
   *
   * @param revision Revision number.
   * @return Revision info.
   */
  @NotNull
  VcsRevision getRevisionInfo(int revision) throws IOException, SVNException;

  /**
   * Get last file update in target revision.
   *
   * @param nodePath       File path.
   * @param beforeRevision Target revision.
   * @return Last file update revision or (< 0, if file not exists).
   * If file is changed in target revision - return target revision.
   */
  int getLastChange(@NotNull String nodePath, int beforeRevision);

  /**
   * Run some work with blocking lock modification.
   */
  @NotNull
  <T> T wrapLockRead(@NotNull LockWorker<T, LockManagerRead> work) throws SVNException, IOException;

  /**
   * Run some work with blocking lock modification.
   */
  @NotNull
  <T> T wrapLockWrite(@NotNull LockWorker<T, LockManagerWrite> work) throws SVNException, IOException;

  /**
   * Create commit writer.
   */
  @NotNull
  VcsWriter createWriter() throws SVNException, IOException;

  @Override
  void close() throws IOException;
}
