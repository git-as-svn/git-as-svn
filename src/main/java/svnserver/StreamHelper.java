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
}
