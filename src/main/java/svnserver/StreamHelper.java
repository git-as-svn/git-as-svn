/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Usefull stream methods.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class StreamHelper {
  private static final int BUFFER_SIZE = 32 * 1024;

  private StreamHelper() {
  }

  public static long copyTo(@NotNull InputStream inputStream, @NotNull OutputStream outputStream) throws IOException {
    byte[] buffer = new byte[BUFFER_SIZE];
    long totalSize = 0;
    while (true) {
      int read = inputStream.read(buffer);
      if (read <= 0) {
        break;
      }
      totalSize += read;
      outputStream.write(buffer, 0, read);
    }
    return totalSize;
  }

  public static int readFully(@NotNull InputStream inputStream, byte[] buffer, int offset, int length) throws IOException {
    int totalRead = 0;
    while (totalRead < length) {
      final int read = inputStream.read(buffer, offset + totalRead, length - totalRead);
      if (read <= 0) {
        break;
      }
      totalRead += read;
    }
    return totalRead;
  }
}
