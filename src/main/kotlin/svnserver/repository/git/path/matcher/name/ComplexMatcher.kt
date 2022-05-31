/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.path.matcher.name

import org.eclipse.jgit.ignore.IMatcher
import svnserver.repository.git.path.NameMatcher

/**
 * Simple matcher for regexp compare.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
data class ComplexMatcher constructor(private val pattern: String, private val dirOnly: Boolean, private val useSvnMask: Boolean) : NameMatcher {
    private val matcher: IMatcher = IMatcher.createPathMatcher(if (dirOnly) pattern.substring(0, pattern.length - 1) else pattern, dirOnly)

    override fun isMatch(name: String, isDir: Boolean): Boolean {
        return matcher.matches(name, isDir, true)
    }

    override val isRecursive: Boolean
        get() {
            return false
        }

    override val svnMask: String?
        get() = if (useSvnMask) pattern else null

    override fun toString(): String {
        return "$pattern${if (dirOnly) "/" else ""}"
    }
}
