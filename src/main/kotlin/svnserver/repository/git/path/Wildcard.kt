/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.path

import org.eclipse.jgit.errors.InvalidPatternException
import svnserver.repository.git.path.matcher.path.AlwaysMatcher
import svnserver.repository.git.path.matcher.path.FileMaskMatcher
import svnserver.repository.git.path.matcher.path.RecursivePathMatcher
import svnserver.repository.git.path.matcher.path.SimplePathMatcher

/**
 * Git wildcard mask.
 *
 *
 * Pattern format: http://git-scm.com/docs/gitignore
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class Wildcard constructor(pattern: String) {
    var matcher: PathMatcher
    var isSvnCompatible: Boolean = false

    companion object {
        private fun hasRecursive(nameMatchers: Array<NameMatcher>): Boolean {
            return nameMatchers.any { matcher -> matcher.isRecursive }
        }

        @Throws(InvalidPatternException::class)
        private fun createNameMatchers(pattern: String): Array<NameMatcher> {
            val tokens: MutableList<String> = WildcardHelper.splitPattern(pattern)
            WildcardHelper.normalizePattern(tokens)
            return tokens.subList(1, tokens.size).map {
                WildcardHelper.nameMatcher(it)
            }.toTypedArray()
        }
    }

    init {
        val nameMatchers: Array<NameMatcher> = createNameMatchers(pattern)
        if (nameMatchers.isNotEmpty()) {
            matcher = if (hasRecursive(nameMatchers)) {
                if ((nameMatchers.size == 2) && nameMatchers[0].isRecursive && !nameMatchers[1].isRecursive) {
                    FileMaskMatcher((nameMatchers[1]))
                } else {
                    RecursivePathMatcher(nameMatchers)
                }
            } else {
                SimplePathMatcher(nameMatchers)
            }
            isSvnCompatible = nameMatchers[nameMatchers.size - 1].svnMask != null
        } else {
            matcher = AlwaysMatcher.INSTANCE
            isSvnCompatible = false
        }
    }
}
