package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.User;

import java.io.IOException;
import java.util.Map;

/**
 * Visitor for update directory on commit.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface VcsCommitBuilder {
  /**
   * Add/copy directory and enter into it.
   *
   * @param name      New directory name.
   * @param sourceDir Directory information.
   */
  void addDir(@NotNull String name, @Nullable VcsFile sourceDir) throws SVNException, IOException;

  /**
   * Enter into directory.
   *
   * @param name Directory name.
   */
  void openDir(@NotNull String name) throws SVNException, IOException;

  /**
   * Chech current directory properties.
   *
   * @param props Properties.
   * @throws SVNException
   * @throws IOException
   */
  void checkDirProperties(@NotNull Map<String, String> props) throws SVNException, IOException;

  /**
   * Leave back from directory.
   */
  void closeDir() throws SVNException, IOException;

  /**
   * Save file (add or update).
   *
   * @param name          File name.
   * @param deltaConsumer Delta consumer from the same repository.
   * @param modify        Modification flag (true - entry modification, false - new entry).
   * @see svnserver.repository.VcsRepository#createFile()
   * @see svnserver.repository.VcsRepository#modifyFile(VcsFile)
   */
  void saveFile(@NotNull String name, @NotNull VcsDeltaConsumer deltaConsumer, boolean modify) throws SVNException, IOException;

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
