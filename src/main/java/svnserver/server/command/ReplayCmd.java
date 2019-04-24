/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import svnserver.parser.SvnServerWriter;
import svnserver.repository.Depth;
import svnserver.repository.SendCopyFrom;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Send revision as is.
 * <p>
 * <pre>
 * replay
 *    params:    ( revision:number low-water-mark:number send-deltas:bool )
 *    After auth exchange completes, server switches to editor command set.
 *    After edit completes, server sends response.
 *    response   ( )
 * </pre>
 *
 * @author a.navrotskiy
 */
public final class ReplayCmd extends BaseCmd<ReplayCmd.Params> {
  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    replayRevision(context, args.revision, args.lowRevision, args.sendDeltas);

    final SvnServerWriter writer = context.getWriter();
    writer
        .listBegin()
        .word("success")
        .listBegin().listEnd()
        .listEnd();
  }

  static void replayRevision(@NotNull SessionContext context, int revision, int lowRevision, boolean sendDeltas) throws IOException, SVNException {
    final DeltaCmd.ReportPipeline pipeline = new DeltaCmd.ReportPipeline(
        new DeltaParams(
            new int[]{revision},
            "",
            "",
            sendDeltas,
            Depth.Infinity,
            SendCopyFrom.OnlyRelative,
            false,
            false,
            lowRevision
        )
    );

    pipeline.setPathReport("", revision - 1, false, SVNDepth.INFINITY);
    pipeline.sendDelta(context);

    final SvnServerWriter writer = context.getWriter();
    writer
        .listBegin()
        .word("finish-replay")
        .listBegin().listEnd()
        .listEnd();
  }

  public static class Params {
    private final int revision;
    private final int lowRevision;
    private final boolean sendDeltas;

    public Params(int revision, int lowRevision, boolean sendDeltas) {
      this.revision = revision;
      this.lowRevision = lowRevision;
      this.sendDeltas = sendDeltas;
    }
  }
}
