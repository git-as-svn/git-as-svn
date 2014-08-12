package svnserver.repository;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Repository interface.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface Repository {
  /**
   * Get latest revision number.
   *
   * @return Latest revision number.
   */
  int getLatestRevision() throws IOException;

  /**
   * Get revision info.
   *
   * @param revision Revision number.
   * @return Revision info.
   */
  @NotNull
  RevisionInfo getRevisionInfo(int revision) throws IOException;
}
