package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * <pre>
 * status
 *    params:   ( target:string recurse:bool ? [ rev:number ] ? depth:word )
 *    Client switches to report command set.
 *    Upon finish-report, server sends auth-request.
 *    After auth exchange completes, server switches to editor command set.
 *    After edit completes, server sends response.
 *    response: ( )
 * </pre>
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class StatusCmd extends DeltaCmd<StatusCmd.Params> {

  public static class Params implements DeltaParams {

    @NotNull
    private final String target;

    /**
     * TODO: issue #25.
     */
    private final boolean recurse;

    @NotNull
    private final int[] rev;

    /**
     * TODO: issue #25.
     */
    @NotNull
    private final String depth;

    public Params(@NotNull String target, boolean recurse, @NotNull int[] rev, @NotNull String depth) {
      this.rev = rev;
      this.target = target;
      this.recurse = recurse;
      this.depth = depth;
    }

    @Override
    public boolean needDeltas() {
      return false;
    }

    @NotNull
    @Override
    public String getPath() {
      return target;
    }

    @Override
    public int getRev(@NotNull SessionContext context) throws IOException {
      return rev.length > 0 ? rev[0] : context.getRepository().getLatestRevision().getId();
    }
  }

  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }
}
