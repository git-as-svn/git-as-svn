/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.network;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.ext.gitlfs.storage.LfsReader;

import java.io.IOException;
import java.io.InputStream;

/**
 * Network storage reader.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsHttpReader implements LfsReader {
  @NotNull
  @Override
  public InputStream openStream() throws IOException {
    return null;
  }

  @Nullable
  @Override
  public InputStream openGzipStream() throws IOException {
    return null;
  }

  @Override
  public long getSize() {
    return 0;
  }

  @Nullable
  @Override
  public String getMd5() {
    return null;
  }

  @NotNull
  @Override
  public String getOid(boolean hashOnly) {
    return null;
  }
}
