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
 * Recursive directory matcher like "**".
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class RecursiveMatcher private constructor() : NameMatcher {
    override fun isMatch(name: String, isDir: Boolean): Boolean {
        return true
    }

    override val svnMask: String?
        get() {
            return null
        }
    override val isRecursive: Boolean
        get() {
            return true
        }

    override fun toString(): String {
        return "**/"
    }

    companion object {
        val INSTANCE: RecursiveMatcher = RecursiveMatcher()
    }
}
