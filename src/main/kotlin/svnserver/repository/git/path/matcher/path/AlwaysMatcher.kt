/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.path.matcher.path

import svnserver.repository.git.path.PathMatcher

/**
 * Matches with any path.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class AlwaysMatcher private constructor() : PathMatcher {
    override fun createChild(name: String, isDir: Boolean): PathMatcher {
        return this
    }

    override val isMatch: Boolean
        get() {
            return true
        }
    override val svnMaskGlobal: String
        get() {
            return "*"
        }

    override fun equals(other: Any?): Boolean {
        return other is AlwaysMatcher
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    override fun toString(): String {
        return "AlwaysMatcher()"
    }

    companion object {
        val INSTANCE: AlwaysMatcher = AlwaysMatcher()
    }
}
