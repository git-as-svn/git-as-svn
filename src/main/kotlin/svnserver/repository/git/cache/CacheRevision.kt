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
class CacheRevision constructor(commitId: ObjectId?, renames: Map<String, String>, fileChange: Map<String, CacheChange>) {
    val gitCommitId: ObjectId? = commitId?.copy()
    private val renames: MutableMap<String, String> = TreeMap()
    private val fileChange: MutableMap<String, CacheChange> = TreeMap()

    constructor(
        svnCommit: RevCommit?,
        renames: Map<String, String>,
        fileChange: Map<String, CacheChange>
    ) : this(svnCommit?.copy(), renames, fileChange)

    fun getRenames(): Map<String, String> {
        return Collections.unmodifiableMap(renames)
    }

    fun getFileChange(): Map<String, CacheChange> {
        return Collections.unmodifiableMap(fileChange)
    }

    init {
        this.renames.putAll(renames)
        this.fileChange.putAll(fileChange)
    }
}
