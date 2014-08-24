package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.User;

import java.io.IOException;

/**
 * Visitor for update directory on commit.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface VcsCommitBuilder {
  /**
   * Add/copy directory and enter into it.
   *
   * @param name    New directory name.
   * @param dirInfo Directory information.
   */
  void addDir(@NotNull String name, @NotNull VcsDirectoryConsumer dirInfo) throws SVNException, IOException;

  /**
   * Enter into directory.
   *
   * @param dir Directory consumer from the same repository.
   */
  void openDir(@NotNull VcsDirectoryConsumer dir) throws SVNException, IOException;

  /**
   * Leave back from directory.
   */
  void closeDir() throws SVNException, IOException;

  /**
   * Save file (add or update).
   *
   * @param deltaConsumer Delta consumer from the same repository.
   * @see svnserver.repository.VcsRepository#createFile(java.lang.String)
   * @see svnserver.repository.VcsRepository#modifyFile(String, int)
   */
  void saveFile(@NotNull VcsDeltaConsumer deltaConsumer) throws SVNException, IOException;

  /**
   * Delete directory or file.
   *
   * @param name Directory/file name.
   */
  void delete(@NotNull String name) throws SVNException, IOException;

  /**
   * Create real commit.
   *
   * @param userInfo User information.
   * @param message  Commit message.
   * @return Returns commitetd revision. If returns null then you need to reattempt to commit data.
   * @throws SVNException
   * @throws IOException
   */
  @Nullable
  VcsRevision commit(@NotNull User userInfo, @NotNull String message) throws SVNException, IOException;

  /**
   * Check last modification revision of path.
   *
   * @param path Full path.
   * @param rev  Revision.
   */
  void checkUpToDate(String path, int rev) throws SVNException, IOException;
}
