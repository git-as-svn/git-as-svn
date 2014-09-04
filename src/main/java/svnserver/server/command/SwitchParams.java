package svnserver.server.command;

import org.jetbrains.annotations.NotNull;

/**
 * <pre>
 * switch
 *    params:   ( [ rev:number ] target:string recurse:bool url:string
 *    ? depth:word ? send_copyfrom_args:bool ignore_ancestry:bool )
 *    Client switches to report command set.
 *    Upon finish-report, server sends auth-request.
 *    After auth exchange completes, server switches to editor command set.
 *    After edit completes, server sends response.
 *    response: ( )
 * </pre>
 * <p>
 * TODO: issue #38.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class SwitchParams extends DeltaParams {

  public SwitchParams(@NotNull int[] rev,
                      @NotNull String target,
                      boolean recurse,
                      @NotNull String url,
                      @NotNull String depth,
                      boolean sendCopyFromArgs,
                      boolean ignoreAncestry) {
    super(rev, target, url, true, Depth.parse(depth, recurse, Depth.Files), sendCopyFromArgs, ignoreAncestry);
  }
}
