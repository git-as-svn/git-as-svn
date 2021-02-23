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
class SimpleMatcher constructor(private val prefix: String, private val suffix: String, private val dirOnly: Boolean) : NameMatcher {
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that: SimpleMatcher = other as SimpleMatcher
        return ((dirOnly == that.dirOnly)
                && ((prefix == that.prefix))
                && (suffix == that.suffix))
    }

    override fun hashCode(): Int {
        var result: Int = prefix.hashCode()
        result = 31 * result + suffix.hashCode()
        result = 31 * result + (if (dirOnly) 1 else 0)
        return result
    }

    override fun toString(): String {
        return prefix + "*" + suffix + (if (dirOnly) "/" else "")
    }
}
