package svnserver.repository.git.prop;

import org.atteo.classindex.IndexSubclasses;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Factory for creating GitProperty.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@IndexSubclasses
public interface GitPropertyFactory {

  /**
   * Return mappeg git file name.
   *
   * @return File name.
   */
  @NotNull
  String getFileName();

  /**
   * Create git property worker by file content.
   *
   * @param content File content.
   * @return Git property worker.
   */
  @NotNull
  GitProperty create(@NotNull String content) throws IOException;
}
