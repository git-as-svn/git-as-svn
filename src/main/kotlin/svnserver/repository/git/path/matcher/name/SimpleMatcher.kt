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
 * Simple matcher for mask with only one asterisk.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
data class SimpleMatcher(private val prefix: String, private val suffix: String, private val dirOnly: Boolean) : NameMatcher {
    override fun isMatch(name: String, isDir: Boolean): Boolean {
        return (!dirOnly || isDir) && (name.length >= prefix.length + suffix.length) && name.startsWith(prefix) && name.endsWith(suffix)
    }

    override val isRecursive: Boolean
        get() {
            return false
        }
    override val svnMask: String
        get() {
            return "$prefix*$suffix"
        }

    override fun toString(): String {
        return "$svnMask${if (dirOnly) "/" else ""}"
    }
}
