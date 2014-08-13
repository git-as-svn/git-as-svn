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
public class UpdateCmd extends DeltaCmd<UpdateCmd.Params> {
  @SuppressWarnings("UnusedDeclaration")
  public static class Params implements DeltaParams {
    private final int[] rev;
    @NotNull
    private final String target;
    private final boolean recurse;
    @NotNull
    private final String depth;

    public Params(int[] rev, @NotNull String target, boolean recurse, @NotNull String depth) {
      this.rev = rev;
      this.target = target;
      this.recurse = recurse;
      this.depth = depth;
    }

    @NotNull
    @Override
    public String getPath() {
      return target;
    }

    @Override
    public int getRev(@NotNull SessionContext context) throws IOException {
      return rev.length > 0 ? rev[0] : context.getRepository().getLatestRevision();
    }
  }

  @NotNull
  @Override
  public Class<Params> getArguments() {
    return Params.class;
  }
}
