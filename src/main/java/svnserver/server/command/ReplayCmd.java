package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import svnserver.parser.SvnServerWriter;
import svnserver.repository.Depth;
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
public class ReplayCmd extends BaseCmd<ReplayCmd.Params> {
  public static class Params {
    private final int revision;
    private final int lowWaterMark;
    private final boolean sendDeltas;

    public Params(int revision, int lowWaterMark, boolean sendDeltas) {
      this.revision = revision;
      this.lowWaterMark = lowWaterMark;
      this.sendDeltas = sendDeltas;
    }
  }

  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }

  @Override
  protected void processCommand(@NotNull SessionContext context, @NotNull Params args) throws IOException, SVNException {
    final DeltaCmd.ReportPipeline pipeline = new DeltaCmd.ReportPipeline(new DeltaParams(new int[]{args.revision}, "", "", args.sendDeltas, Depth.Infinity, true, false, false));
    pipeline.setPathReport("", args.revision - 1, false, SVNDepth.INFINITY);
    pipeline.sendDelta(context, "", args.revision);

    final SvnServerWriter writer = context.getWriter();
    writer
        .listBegin()
        .word("finish-replay")
        .listBegin().listEnd()
        .listEnd();
    writer
        .listBegin()
        .word("success")
        .listBegin().listEnd()
        .listEnd();
  }
}
