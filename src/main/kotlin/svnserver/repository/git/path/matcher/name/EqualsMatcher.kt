/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.path.matcher.name

import svnserver.repository.git.path.NameMatcher

/**
 * Simple matcher for equals compare.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
data class EqualsMatcher constructor(override val svnMask: String, private val dirOnly: Boolean) : NameMatcher {
    override fun isMatch(name: String, isDir: Boolean): Boolean {
        return (!dirOnly || isDir) && (svnMask == name)
    }

    override val isRecursive: Boolean
        get() {
            return false
        }

    override fun toString(): String {
        return "$svnMask${if (dirOnly) "/" else ""}"
    }
}
