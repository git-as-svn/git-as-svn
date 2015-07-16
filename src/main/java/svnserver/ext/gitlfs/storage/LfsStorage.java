/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.context.Shared;

import java.io.IOException;

/**
 * GIT LFS storage interface.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface LfsStorage extends Shared {
  @NotNull
  String OID_PREFIX = "sha256:";

  /**
   * Create reader for object by SHA-256 hash.
   *
   * @param oid Object hash.
   * @return Object reader or null if object not exists.
   * @throws IOException .
   */
  @Nullable
  LfsReader getReader(@NotNull String oid) throws IOException;

  /**
   * Create writer for object.
   *
   * @return Object writer.
   * @throws IOException
   */
  @NotNull
  LfsWriter getWriter() throws IOException;
}
