package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;

/**
 * Repository interface.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface VcsRepository {
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
  int getLatestRevision() throws IOException;

  /**
   * Update revision information.
   *
   * @throws IOException
   */
  void updateRevisions() throws IOException;

  /**
   * Get revision info.
   *
   * @param revision Revision number.
   * @return Revision info.
   */
  @NotNull
  VcsRevision getRevisionInfo(int revision) throws IOException, SVNException;

  /**
   * Create new file in repository.
   *
   * @param path File path in repository.
   * @return File updater.
   */
  @NotNull
  VcsDeltaConsumer createFile(@NotNull String path) throws IOException, SVNException;

  /**
   * Modification of the existing file.
   *
   * @param revision File revision (for need update check).
   * @param path     File path in repository.
   * @return File updater.
   */
  @NotNull
  VcsDeltaConsumer modifyFile(@NotNull String path, int revision) throws IOException, SVNException;

  /**
   * Get information for deleting file.
   *
   * @param revision File revision (for need update check).
   * @param path     File path in repository.
   * @return Removing file info.
   * @throws IOException
   * @throws SVNException
   */
  @NotNull
  VcsFile deleteEntry(@NotNull String path, int revision) throws IOException, SVNException;

  /**
   * Create tree for commit.
   *
   * @return Commit build.
   */
  @NotNull
  VcsCommitBuilder createCommitBuilder() throws IOException;
}
