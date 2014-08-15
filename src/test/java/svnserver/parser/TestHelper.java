package svnserver.parser;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Common test functions.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class TestHelper {
  public static void saveFile(@NotNull File file, @NotNull String content) throws IOException {
    try (OutputStream stream = new FileOutputStream(file)) {
      stream.write(content.getBytes(StandardCharsets.UTF_8));
    }
  }
}
