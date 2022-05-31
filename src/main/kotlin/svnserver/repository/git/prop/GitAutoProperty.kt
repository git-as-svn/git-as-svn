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
import org.tmatesoft.svn.core.SVNProperty
import svnserver.repository.git.path.PathMatcher
import java.util.concurrent.ConcurrentHashMap

/**
 * Parse and processing .gitignore.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal data class GitAutoProperty private constructor(private val matcher: PathMatcher, private val property: String, private val value: String) : GitProperty {
    override fun apply(props: MutableMap<String, String>) {
        val mask: String? = matcher.svnMaskGlobal
        if (mask != null) {
            var autoprops: String = props.getOrDefault(SVNProperty.INHERITABLE_AUTO_PROPS, "")
            var beg = 0
            while (true) {
                if (autoprops.substring(beg).startsWith(mask + MASK_SEPARATOR)) {
                    var end: Int = autoprops.indexOf('\n', beg + 1)
                    if (end < 0) {
                        end = autoprops.length
                    }
                    autoprops = (autoprops.substring(0, end)
                            + "; " + property + "=" + value
                            + autoprops.substring(end))
                    break
                }
                beg = autoprops.indexOf('\n', beg + 1)
                if (beg < 0) {
                    autoprops = (autoprops
                            + mask
                            + MASK_SEPARATOR
                            + property + "=" + value + "\n")
                    break
                }
            }
            props[SVNProperty.INHERITABLE_AUTO_PROPS] = autoprops
        }
    }

    override val filterName: String?
        get() {
            return null
        }

    override fun createForChild(name: String, mode: FileMode): GitProperty? {
        if (mode.objectType == Constants.OBJ_BLOB) {
            return null
        }
        if (matcher.svnMaskGlobal != null) {
            return null
        }
        val matcherChild: PathMatcher = matcher.createChild(name, true) ?: return null
        return GitAutoProperty(matcherChild, property, value)
    }

    companion object {
        private const val MASK_SEPARATOR: String = " = "

        private val cache = ConcurrentHashMap<GitAutoProperty, GitAutoProperty>()

        fun create(matcher: PathMatcher, property: String, value: String): GitAutoProperty {
            val result = GitAutoProperty(matcher, property, value)
            return cache.computeIfAbsent(result) { result }
        }
    }
}
