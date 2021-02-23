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
import java.util.*

/**
 * Matcher for patterns without "**".
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class SimplePathMatcher private constructor(private val nameMatchers: Array<NameMatcher>, private val index: Int) : PathMatcher {
    constructor(nameMatchers: Array<NameMatcher>) : this(nameMatchers, 0)

    override fun createChild(name: String, isDir: Boolean): PathMatcher? {
        if (nameMatchers[index].isMatch(name, isDir)) {
            if (index + 1 == nameMatchers.size) {
                return AlwaysMatcher.INSTANCE
            }
            return SimplePathMatcher(nameMatchers, index + 1)
        }
        return null
    }

    override val isMatch: Boolean
        get() {
            return false
        }
    override val svnMaskLocal: String?
        get() {
            if (index + 1 == nameMatchers.size) {
                return nameMatchers[index].svnMask
            }
            return null
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that: SimplePathMatcher = other as SimplePathMatcher
        if (nameMatchers.size - index != that.nameMatchers.size - that.index) return false
        for (i in index until nameMatchers.size) {
            if (!Objects.equals(nameMatchers[i], that.nameMatchers[i + that.index - index])) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 0
        for (i in index until nameMatchers.size) {
            result = 31 * result + nameMatchers[i].hashCode()
        }
        return result
    }

    override fun toString(): String {
        val sb: StringBuilder = StringBuilder()
        for (i in index until nameMatchers.size) {
            sb.append(nameMatchers[i].toString())
        }
        return sb.toString()
    }
}
