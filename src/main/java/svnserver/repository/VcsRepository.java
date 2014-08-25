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
  @NotNull
  VcsRevision getLatestRevision() throws IOException;

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
   * @return File updater.
   */
  @NotNull
  VcsDeltaConsumer createFile() throws IOException, SVNException;

  /**
   * Modification of the existing file.
   *
   * @param file File for modification.
   * @return File updater.
   */
  @NotNull
  VcsDeltaConsumer modifyFile(@NotNull VcsFile file) throws IOException, SVNException;

  /**
   * Create tree for commit.
   *
   * @return Commit build.
   * @throws IOException
   */
  @NotNull
  VcsCommitBuilder createCommitBuilder() throws IOException, SVNException;
}
