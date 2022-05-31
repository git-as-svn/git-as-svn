/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.path.matcher.path

import svnserver.repository.git.path.NameMatcher
import svnserver.repository.git.path.PathMatcher

/**
 * Complex full-feature pattern matcher.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
data class FileMaskMatcher constructor(private val matcher: NameMatcher) : PathMatcher {
    override fun createChild(name: String, isDir: Boolean): PathMatcher? {
        if (matcher.isMatch(name, isDir)) {
            return AlwaysMatcher.INSTANCE
        }
        if (!isDir) {
            return null
        }
        return this
    }

    override val isMatch: Boolean
        get() {
            return false
        }
    override val svnMaskGlobal: String?
        get() {
            return matcher.svnMask
        }
}
