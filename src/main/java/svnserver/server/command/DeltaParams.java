package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import svnserver.server.SessionContext;

import java.io.IOException;
import java.util.Locale;

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

  /**
   * TODO: issue #25.
   */
  @NotNull
  Depth getDepth();

  enum Depth {
    Exclude,
    Unknown,
    Empty,
    Files,
    Immediates,
    Infinity;

    @NotNull
    public static Depth parse(@NotNull String depthStr, boolean recurse) {
      if (depthStr.isEmpty())
        return recurse ? Infinity : Files;

      for (Depth depth : values())
        if (depth.name().toLowerCase(Locale.ENGLISH).equals(depthStr))
          return depth;

      return Unknown;
    }
  }
}
