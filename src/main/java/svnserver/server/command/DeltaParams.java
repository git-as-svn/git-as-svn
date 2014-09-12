package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Delta parameters.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public class DeltaParams {

  @Nullable
  private final SVNURL targetPath;

  @NotNull
  private final int[] rev;

  @NotNull
  private final String path;

  @NotNull
  private final Depth depth;

  /**
   * TODO: issue #35, copy detection.
   */
  private final boolean sendCopyFromArgs;

  private final boolean textDeltas;

  public DeltaParams(@NotNull int[] rev,
                     @NotNull String path,
                     @NotNull String targetPath,
                     boolean textDeltas,
                     @NotNull Depth depth,
                     boolean sendCopyFromArgs,
                     /**
                      * Broken-minded SVN feature we're unlikely to support EVER.
                      * <p>
                      * If {@code ignoreAncestry} is {@code false} and file was deleted and created back between source and target revisions,
                      * SVN server sends two deltas for this file - deletion and addition. The only effect that this behavior produces is
                      * increased number of tree conflicts on client.
                      * <p>
                      * Worse, in SVN it is possible to delete file and create it back in the same commit, effectively breaking its history.
                      */
                     @SuppressWarnings("UnusedParameters")
                     boolean ignoreAncestry) throws SVNException {
    this.rev = rev;
    this.path = path;
    this.targetPath = targetPath.isEmpty() ? null : SVNURL.parseURIEncoded(targetPath);
    this.depth = depth;
    this.sendCopyFromArgs = sendCopyFromArgs;
    this.textDeltas = textDeltas;
  }

  @Nullable
  public SVNURL getTargetPath() {
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

  @NotNull
  public Depth getDepth() {
    return depth;
  }

}
