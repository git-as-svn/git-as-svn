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
 * Complex full-feature pattern matcher.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class RecursivePathMatcher private constructor(private val nameMatchers: Array<NameMatcher>, private val indexes: IntArray) : PathMatcher {
    constructor(nameMatchers: Array<NameMatcher>) : this(nameMatchers, START_ARRAY)

    override fun createChild(name: String, isDir: Boolean): PathMatcher? {
        val childs = IntArray(indexes.size * 2)
        var changed = false
        var count = 0
        for (index: Int in indexes) {
            if (nameMatchers[index].isMatch(name, isDir)) {
                if (nameMatchers[index].isRecursive) {
                    childs[count++] = index
                    if (nameMatchers[index + 1].isMatch(name, isDir)) {
                        if (index + 2 == nameMatchers.size) {
                            return AlwaysMatcher.INSTANCE
                        }
                        childs[count++] = index + 2
                        changed = true
                    }
                } else {
                    if (index + 1 == nameMatchers.size) {
                        return AlwaysMatcher.INSTANCE
                    }
                    childs[count++] = index + 1
                    changed = true
                }
            } else {
                changed = true
            }
        }
        if (!isDir) {
            return null
        }
        if (!changed) {
            return this
        }
        return if (count == 0) null else RecursivePathMatcher(nameMatchers, childs.copyOf(count))
    }

    override val isMatch: Boolean
        get() {
            return false
        }
    override val svnMaskGlobal: String?
        get() {
            for (index: Int in indexes) {
                if (index + 2 == nameMatchers.size) {
                    if (nameMatchers[index].isRecursive) {
                        return nameMatchers[index + 1].svnMask
                    }
                }
            }
            return null
        }
    override val svnMaskLocal: String?
        get() {
            for (index: Int in indexes) {
                if (index + 1 == nameMatchers.size) {
                    return nameMatchers[index].svnMask
                }
            }
            return null
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that: RecursivePathMatcher = other as RecursivePathMatcher
        if (indexes.size != that.indexes.size) return false
        val offset: Int = indexes[0]
        val thatOffset: Int = that.indexes[0]
        if (nameMatchers.size - offset != that.nameMatchers.size - thatOffset) return false
        val shift: Int = thatOffset - offset
        for (i in offset until indexes.size) {
            if (indexes[i] != that.indexes[i + shift]) {
                return false
            }
        }
        for (i in offset until nameMatchers.size) {
            if (!Objects.equals(nameMatchers[i], that.nameMatchers[i + shift])) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        val offset: Int = indexes[0]
        var result = 0
        for (index: Int in indexes) {
            result = 31 * (index - offset)
            assert((offset <= index))
        }
        for (i in offset until nameMatchers.size) {
            result = 31 * result + nameMatchers[i].hashCode()
        }
        return result
    }

    companion object {
        private val START_ARRAY: IntArray = intArrayOf(0)
    }
}
