package svnserver.repository;

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
  RevisionInfo getRevisionInfo(int revision) throws IOException;

}
