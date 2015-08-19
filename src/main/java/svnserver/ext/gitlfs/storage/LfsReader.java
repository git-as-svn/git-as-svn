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

import java.io.IOException;
import java.io.InputStream;

/**
 * Interface for reading LFS file.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface LfsReader {
  /**
   * Open object stream.
   */
  @NotNull
  InputStream openStream() throws IOException;

  /**
   * Open gzip-compressed object stream.
   *
   * @return Can return null if not supported.
   */
  @Nullable
  InputStream openGzipStream() throws IOException;

  /**
   * Object size.
   */
  long getSize();

  /**
   * MD5 object checksum.
   */
  @Nullable
  String getMd5();

  /**
   * Object id.
   */
  @NotNull
  String getOid(boolean hashOnly);
}
