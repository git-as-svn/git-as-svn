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
 * Update between revisions.
 * <pre>
 * diff
 * params:   ( [ rev:number ] target:string recurse:bool ignore-ancestry:bool
 * url:string ? text-deltas:bool ? depth:word )
 * Client switches to report command set.
 * Upon finish-report, server sends auth-request.
 * After auth exchange completes, server switches to editor command set.
 * After edit completes, server sends response.
 * response: ( )
</pre> *
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class DiffParams constructor(
    rev: IntArray,
    target: String,
    recurse: Boolean,
    ignoreAncestry: Boolean,
    url: String,
    textDeltas: Boolean,
    depth: String
) : DeltaParams(rev, target, url, textDeltas, Depth.parse(depth, recurse, Depth.Files), SendCopyFrom.Never, ignoreAncestry, true, 0)
