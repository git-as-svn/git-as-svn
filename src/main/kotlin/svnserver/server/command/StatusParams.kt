/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command

import svnserver.repository.Depth
import svnserver.repository.SendCopyFrom

/**
 * <pre>
 * status
 * params:   ( target:string recurse:bool ? [ rev:number ] ? depth:word )
 * Client switches to report command set.
 * Upon finish-report, server sends auth-request.
 * After auth exchange completes, server switches to editor command set.
 * After edit completes, server sends response.
 * response: ( )
</pre> *
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class StatusParams(
    target: String,
    recurse: Boolean,
    rev: IntArray,
    depth: String
) : DeltaParams(rev, target, "", false, Depth.parse(depth, recurse, Depth.Empty), SendCopyFrom.Never, false, true, 0)
