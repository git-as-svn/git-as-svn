package svnserver.repository;

import org.jetbrains.annotations.NotNull;

/**
 * Visitor for update directory on commit.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface VcsTreeBuilder {
  /**
   * Add new directory and enter into it.
   */
  void addDir(@NotNull String name);

  /**
   * Enter into directory.
   *
   * @param name Directory name.
   */
  void openDir(@NotNull String name);

  /**
   * Leave back from directory.
   */
  void closeDir();
}
