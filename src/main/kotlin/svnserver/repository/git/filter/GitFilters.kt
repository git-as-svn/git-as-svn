/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.filter

import svnserver.context.LocalContext
import svnserver.ext.gitlfs.LfsFilter
import svnserver.ext.gitlfs.storage.LfsStorage

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class GitFilters constructor(context: LocalContext, lfsStorage: LfsStorage?) {
    val raw: GitFilter
    val link: GitFilter
    private val filters: Array<GitFilter>
    operator fun get(name: String): GitFilter? {
        for (filter: GitFilter in filters) if ((filter.name == name)) return filter
        return null
    }

    init {
        raw = GitFilterRaw(context)
        link = GitFilterLink(context)
        filters = arrayOf(
            raw,
            link,
            GitFilterGzip(context),
            LfsFilter(context, lfsStorage)
        )
    }
}
