/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.cache

import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import java.util.*

/**
 * Revision cache information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class CacheRevision(commitId: ObjectId?, val renames: Map<String, String>, val fileChange: Map<String, CacheChange>) {
    val gitCommitId = commitId?.copy()

    constructor(
        svnCommit: RevCommit?,
        renames: Map<String, String>,
        fileChange: Map<String, CacheChange>
    ) : this(svnCommit?.copy(), renames, fileChange)
}
