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

  /**
   * TODO: issue #28.
   * <p>
   * svn diff StringHelper.java@34 svn://localhost/git-as-svn/src/main/java/svnserver/SvnConstants.java@33
   * <p>
   * WARNING! Check ACL!
   */
  @Nullable
  private final String tgtPath;

  @NotNull
  private final int[] rev;

  @NotNull
  private final String target;

  /**
   * TODO: issue #25.
   */
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
                     @NotNull String target,
                     @Nullable String tgtPath,
                     boolean textDeltas, boolean recurse,
                     @NotNull String depth,
                     boolean sendCopyFromArgs,
                     boolean ignoreAncestry) {
    this.rev = rev;
    this.target = target;
    this.tgtPath = tgtPath;
    this.depth = Depth.parse(depth, recurse);
    this.sendCopyFromArgs = sendCopyFromArgs;
    this.ignoreAncestry = ignoreAncestry;
    this.textDeltas = textDeltas;
  }

  @NotNull
  public String getTgtPath() {
    return tgtPath;
  }

  @NotNull
  public String getPath() {
    return target;
  }

  public int getRev(@NotNull SessionContext context) throws IOException {
    return rev.length > 0 ? rev[0] : context.getRepository().getLatestRevision().getId();
  }

  public boolean needDeltas() {
    return textDeltas;
  }

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
