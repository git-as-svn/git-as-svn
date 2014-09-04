package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.server.SessionContext;

import java.io.IOException;
import java.util.Locale;

/**
 * Delta parameters.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public class DeltaParams {

  @Nullable
  private final String targetPath;

  @NotNull
  private final int[] rev;

  @NotNull
  private final String path;

  @NotNull
  private final Depth depth;

  /**
   * TODO: issue #25.
   */
  private final boolean sendCopyFromArgs;

  /**
   * TODO: issue #25.
   */
  private final boolean ignoreAncestry;

  private final boolean textDeltas;

  public DeltaParams(@NotNull int[] rev,
                     @NotNull String path,
                     @Nullable String targetPath,
                     boolean textDeltas,
                     @NotNull Depth depth,
                     boolean sendCopyFromArgs,
                     boolean ignoreAncestry) {
    this.rev = rev;
    this.path = path;
    this.targetPath = targetPath;
    this.depth = depth;
    this.sendCopyFromArgs = sendCopyFromArgs;
    this.ignoreAncestry = ignoreAncestry;
    this.textDeltas = textDeltas;
  }

  /**
   * TODO: issue #28.
   * <p>
   * svn diff StringHelper.java@34 svn://localhost/git-as-svn/src/main/java/svnserver/SvnConstants.java@33
   * <p>
   * WARNING! Check ACL!
   */
  @Nullable
  public String getTargetPath() {
    return targetPath;
  }

  @NotNull
  public String getPath() {
    return path;
  }

  public int getRev(@NotNull SessionContext context) throws IOException {
    return rev.length > 0 ? rev[0] : context.getRepository().getLatestRevision().getId();
  }

  public boolean needDeltas() {
    return textDeltas;
  }

  /**
   * TODO: issue #25.
   */
  @NotNull
  public Depth getDepth() {
    return depth;
  }

  public static enum Depth {
    Exclude,
    Unknown,
    Empty,
    Files,
    Immediates,
    Infinity;

    @NotNull
    public static Depth parse(@NotNull String depthStr, boolean recurse, @NotNull Depth nonRecurse) {
      if (depthStr.isEmpty())
        return recurse ? Infinity : nonRecurse;

      for (Depth depth : values())
        if (depth.name().toLowerCase(Locale.ENGLISH).equals(depthStr))
          return depth;

      return Unknown;
    }
  }
}
