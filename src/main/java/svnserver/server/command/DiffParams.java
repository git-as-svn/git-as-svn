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
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class DiffParams extends DeltaParams {

  public DiffParams(
      @NotNull int[] rev,
      @NotNull String target,
      boolean recurse,
      boolean ignoreAncestry,
      @NotNull String url,
      boolean textDeltas,
      @NotNull String depth
  ) throws SVNException {
    super(rev, target, url, textDeltas, Depth.parse(depth, recurse, Depth.Files), SendCopyFrom.Never, ignoreAncestry, true);
  }
}
