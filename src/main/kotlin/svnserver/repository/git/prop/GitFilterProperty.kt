/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop

import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import svnserver.repository.git.path.PathMatcher

/**
 * Replace file filter.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal class GitFilterProperty
/**
 * Set property to all matched file.
 *
 * @param matcher    File matcher.
 * @param myFilterName Filter name.
 */ constructor(private val matcher: PathMatcher, private val myFilterName: String) : GitProperty {
    override fun apply(props: MutableMap<String, String>) {}
    override val filterName: String?
        get() = if (matcher.isMatch) myFilterName else null

    override fun createForChild(name: String, mode: FileMode): GitProperty? {
        val isDir: Boolean = mode.objectType != Constants.OBJ_BLOB
        val matcherChild: PathMatcher? = matcher.createChild(name, isDir)
        if ((matcherChild != null) && (isDir || matcherChild.isMatch)) {
            return GitFilterProperty(matcherChild, myFilterName)
        }
        return null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that: GitFilterProperty = other as GitFilterProperty
        return ((matcher == that.matcher) && (filterName == that.filterName))
    }

    override fun hashCode(): Int {
        var result: Int = matcher.hashCode()
        result = 31 * result + filterName.hashCode()
        return result
    }
}
