package svnserver.server.command;

import org.jetbrains.annotations.NotNull;

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
public final class StatusParams extends DeltaParams {

  public StatusParams(@NotNull String target,
                      boolean recurse,
                      @NotNull int[] rev,
                      @NotNull String depth) {
    super(rev, target, null, false, Depth.parse(depth, recurse, Depth.Empty), false, false);
  }
}
