package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Delta parameters.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface DeltaParams {
  @NotNull
  String getPath();

  int getRev(@NotNull SessionContext context) throws IOException;

  boolean needDeltas();
}
