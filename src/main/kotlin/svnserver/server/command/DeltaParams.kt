/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command

import org.tmatesoft.svn.core.SVNURL
import svnserver.repository.Depth
import svnserver.repository.SendCopyFrom
import svnserver.server.SessionContext

/**
 * Delta parameters.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
open class DeltaParams internal constructor(
    private val rev: IntArray,
    val path: String,
    targetPath: String,
    val textDeltas: Boolean,
    val depth: Depth?,
    val sendCopyFrom: SendCopyFrom,  /*
       * Broken-minded SVN feature we're unlikely to support EVER.
       * <p>
       * If {@code ignoreAncestry} is {@code false} and file was deleted and created back between source and target revisions,
       * SVN server sends two deltas for this file - deletion and addition. The only effect that this behavior produces is
       * increased number of tree conflicts on client.
       * <p>
       * Worse, in SVN it is possible to delete file and create it back in the same commit, effectively breaking its history.
       */
    val ignoreAncestry: Boolean,
    val includeInternalProps: Boolean,
    val lowRevision: Int
) {
    val targetPath: SVNURL? = if (targetPath.isEmpty()) null else SVNURL.parseURIEncoded(targetPath)
    fun getRev(context: SessionContext): Int {
        return if (rev.isNotEmpty()) rev[0] else context.branch.latestRevision.id
    }
}
