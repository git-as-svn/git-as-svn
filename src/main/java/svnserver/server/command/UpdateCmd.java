package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Update between revisions.
 * <pre>
 * update
 *    params:   ( [ rev:number ] target:string recurse:bool
 *    ? depth:word send_copyfrom_args:bool ? ignore_ancestry:bool )
 *    Client switches to report command set.
 *    Upon finish-report, server sends auth-request.
 *    After auth exchange completes, server switches to editor command set.
 *    After edit completes, server sends response.
 *    response: ( )
 * </pre>
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class UpdateCmd extends DeltaCmd<UpdateCmd.Params> {

  public static class Params implements DeltaParams {

    private final int[] rev;

    @NotNull
    private final String target;

    /**
     * TODO: issue #25.
     */
    private final boolean recurse;

    /**
     * TODO: issue #25.
     */
    @NotNull
    private final String depth;

    /**
     * TODO: issue #25.
     */
    private final boolean sendCopyFromArgs;

    /**
     * TODO: issue #25.
     */
    private final boolean ignoreAncestry;

    public Params(int[] rev, @NotNull String target, boolean recurse, @NotNull String depth, boolean sendCopyFromArgs, boolean ignoreAncestry) {
      this.rev = rev;
      this.target = target;
      this.recurse = recurse;
      this.depth = depth;
      this.sendCopyFromArgs = sendCopyFromArgs;
      this.ignoreAncestry = ignoreAncestry;
    }

    @Override
    public boolean needDeltas() {
      return true;
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
