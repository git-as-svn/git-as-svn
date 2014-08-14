package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;

/**
 * Visitor for update directory on commit.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface VcsCommitBuilder {
  /**
   * Add new directory and enter into it.
   */
  void addDir(@NotNull String name) throws SVNException, IOException;

  /**
   * Enter into directory.
   *
   * @param name Directory name.
   */
  void openDir(@NotNull String name) throws SVNException, IOException;

  /**
   * Leave back from directory.
   */
  void closeDir() throws SVNException, IOException;

  /**
   * Save file (add or update).
   *
   * @param name          File name.
   * @param deltaConsumer Delta consumer from the same repository.
   * @see svnserver.repository.VcsRepository#createFile(java.lang.String)
   * @see svnserver.repository.VcsRepository#modifyFile(String, int)
   */
  void saveFile(@NotNull String name, @NotNull VcsDeltaConsumer deltaConsumer) throws SVNException, IOException;

  /**
   * Create real commit.
   *
   * @param message Commit message.
   * @throws SVNException
   * @throws IOException
   */
  void commit(@NotNull String message) throws SVNException, IOException;
}
