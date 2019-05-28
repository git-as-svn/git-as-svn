/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import svnserver.repository.Depth;
import svnserver.repository.SendCopyFrom;
import svnserver.server.SessionContext;

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

  @NotNull
  private final SendCopyFrom sendCopyFrom;

  private final boolean includeInternalProps;

  private final boolean textDeltas;

  private final int lowRevision;

  DeltaParams(
      @NotNull int[] rev,
      @NotNull String path,
      @NotNull String targetPath,
      boolean textDeltas,
      @NotNull Depth depth,
      @NotNull SendCopyFrom sendCopyFrom,
      /*
       * Broken-minded SVN feature we're unlikely to support EVER.
       * <p>
       * If {@code ignoreAncestry} is {@code false} and file was deleted and created back between source and target revisions,
       * SVN server sends two deltas for this file - deletion and addition. The only effect that this behavior produces is
       * increased number of tree conflicts on client.
       * <p>
       * Worse, in SVN it is possible to delete file and create it back in the same commit, effectively breaking its history.
       */
      @SuppressWarnings("UnusedParameters") boolean ignoreAncestry,
      boolean includeInternalProps,
      int lowRevision
  ) throws SVNException {
    this.rev = rev;
    this.path = path;
    this.targetPath = targetPath.isEmpty() ? null : SVNURL.parseURIEncoded(targetPath);
    this.depth = depth;
    this.sendCopyFrom = sendCopyFrom;
    this.textDeltas = textDeltas;
    this.includeInternalProps = includeInternalProps;
    this.lowRevision = lowRevision;
  }

  @Nullable
  SVNURL getTargetPath() {
    return targetPath;
  }

  @NotNull
  public String getPath() {
    return path;
  }

  int getRev(@NotNull SessionContext context) {
    return rev.length > 0 ? rev[0] : context.getBranch().getLatestRevision().getId();
  }

  boolean sendDeltas() {
    return textDeltas;
  }

  @NotNull
  public Depth getDepth() {
    return depth;
  }

  @NotNull
  SendCopyFrom getSendCopyFrom() {
    return sendCopyFrom;
  }

  boolean isIncludeInternalProps() {
    return includeInternalProps;
  }

  int getLowRevision() {
    return lowRevision;
  }
}
