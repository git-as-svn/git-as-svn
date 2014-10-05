package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Send revisions as is.
 * <p>
 * <pre>
 * replay-range
 *    params:    ( start-rev:number end-rev:number low-water-mark:number
 *                 send-deltas:bool )
 *    After auth exchange completes, server sends each revision
 *    from start-rev to end-rev, alternating between sending 'revprops'
 *    entries and sending the revision in the editor command set.
 *    After all revisions are complete, server sends response.
 *    revprops:  ( revprops:word props:proplist )
 *      (revprops here is the literal word "revprops".)
 *    response   ( )
 * </pre>
 *
 * @author a.navrotskiy
 */
public class ReplayRangeCmd extends BaseCmd<ReplayRangeCmd.Params> {
  public static class Params {
    private final int startRev;
    private final int endRev;
    private final int lowWaterMark;
    private final boolean sendDeltas;

    public Params(int startRev, int endRev, int lowWaterMark, boolean sendDeltas) {
      this.startRev = startRev;
      this.endRev = endRev;
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

  }

}
