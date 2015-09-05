/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.context;

import org.apache.http.annotation.ThreadSafe;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;

/**
 * Interface for objects in SharedContext.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ThreadSafe
public interface Shared extends AutoCloseable {
  /**
   * Initialize item.
   * Can be executed multiple times.
   */
  default void init(@NotNull SharedContext context) throws IOException, SVNException {
  }

  /**
   * Run on server ready to work,
   */
  default void ready(@NotNull SharedContext context) throws IOException {
  }

  @Override
  default void close() throws Exception {
  }
}
