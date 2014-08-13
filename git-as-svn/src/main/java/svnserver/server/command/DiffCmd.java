package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import svnserver.server.SessionContext;

import java.io.IOException;

/**
 * Update between revisions.
 * <pre>
 * diff
 *    params:   ( [ rev:number ] target:string recurse:bool ignore-ancestry:bool
 *    url:string ? text-deltas:bool ? depth:word )
 *    Client switches to report command set.
 *    Upon finish-report, server sends auth-request.
 *    After auth exchange completes, server switches to editor command set.
 *    After edit completes, server sends response.
 *    response: ( )
 * </pre>
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class DiffCmd extends DeltaCmd<DiffCmd.Params> {
  @SuppressWarnings("UnusedDeclaration")
  public static class Params implements DeltaParams {
    private final int[] rev;
    @NotNull
    private final String target;
    private final boolean recurse;
    private final boolean ignoreAncestry;
    @NotNull
    private final String url;
    private final boolean textDeltas;
    @NotNull
    private final String depth;

    public Params(int[] rev, @NotNull String target, boolean recurse, boolean ignoreAncestry, @NotNull String url, boolean textDeltas, @NotNull String depth) {
      this.rev = rev;
      this.target = target;
      this.recurse = recurse;
      this.ignoreAncestry = ignoreAncestry;
      this.url = url;
      this.textDeltas = textDeltas;
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
