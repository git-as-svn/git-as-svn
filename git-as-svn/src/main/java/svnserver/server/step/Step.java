package svnserver.server.step;

import org.jetbrains.annotations.NotNull;
import svnserver.server.SessionContext;
import svnserver.server.error.ClientErrorException;

import java.io.IOException;

/**
 * Step interface.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@FunctionalInterface
public interface Step {
  /**
   * Process step.
   *
   * @param context Process step.
   */
  void process(@NotNull SessionContext context) throws IOException, ClientErrorException;
}
