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
import java.util.concurrent.ConcurrentHashMap

/**
 * Parse and processing .gitignore.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal data class GitFileProperty private constructor(private val matcher: PathMatcher, private val property: String, private val value: String?) : GitProperty {
    override fun apply(props: MutableMap<String, String>) {}
    override val filterName: String?
        get() {
            return null
        }

    override fun createForChild(name: String, mode: FileMode): GitProperty? {
        val isDir: Boolean = mode.objectType != Constants.OBJ_BLOB
        val matcherChild: PathMatcher? = matcher.createChild(name, isDir)
        if (matcherChild != null) {
            if (isDir) {
                return create(matcherChild, property, value)
            } else if (matcherChild.isMatch) {
                return SimpleFileProperty.create(property, value)
            }
        }
        return null
    }

    companion object {
        // Without this cache, we have 60 *millions* of instances on at-git.mail.msk
        private val cache = ConcurrentHashMap<GitFileProperty, GitFileProperty>()

        fun create(matcher: PathMatcher, filterName: String, value: String?): GitFileProperty {
            val result = GitFileProperty(matcher, filterName, value)
            return cache.computeIfAbsent(result) { result }
        }
    }
}
