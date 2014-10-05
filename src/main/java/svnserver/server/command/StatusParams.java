/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.repository.Depth;
import svnserver.repository.SendCopyFrom;

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

  public StatusParams(
      @NotNull String target,
      boolean recurse,
      @NotNull int[] rev,
      @NotNull String depth
  ) throws SVNException {
    super(rev, target, "", false, Depth.parse(depth, recurse, Depth.Empty), SendCopyFrom.Never, false, true);
  }
}
