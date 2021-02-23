/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.path

import org.eclipse.jgit.errors.InvalidPatternException
import svnserver.repository.git.path.matcher.name.ComplexMatcher
import svnserver.repository.git.path.matcher.name.EqualsMatcher
import svnserver.repository.git.path.matcher.name.RecursiveMatcher
import svnserver.repository.git.path.matcher.name.SimpleMatcher
import java.util.*

/**
 * Git wildcard mask.
 *
 *
 * Pattern format: http://git-scm.com/docs/gitignore
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal object WildcardHelper {
    private const val PATH_SEPARATOR: Char = '/'

    @Throws(InvalidPatternException::class)
    fun nameMatcher(mask: String): NameMatcher {
        if ((mask == "**/")) {
            return RecursiveMatcher.INSTANCE
        }
        val dirOnly: Boolean = mask.endsWith("/")
        val nameMask: String = tryRemoveBackslashes(if (dirOnly) mask.substring(0, mask.length - 1) else mask)
        if ((nameMask.indexOf('[') < 0) && (nameMask.indexOf(']') < 0) && (nameMask.indexOf('\\') < 0)) {
            // Subversion compatible mask.
            if (nameMask.indexOf('?') < 0) {
                val asterisk: Int = nameMask.indexOf('*')
                if (asterisk < 0) {
                    return EqualsMatcher(nameMask, dirOnly)
                } else if (mask.indexOf('*', asterisk + 1) < 0) {
                    return SimpleMatcher(nameMask.substring(0, asterisk), nameMask.substring(asterisk + 1), dirOnly)
                }
            }
            return ComplexMatcher(nameMask, dirOnly, true)
        } else {
            return ComplexMatcher(nameMask, dirOnly, false)
        }
    }

    fun tryRemoveBackslashes(pattern: String): String {
        val result: StringBuilder = StringBuilder(pattern.length)
        var start = 0
        while (true) {
            val next: Int = pattern.indexOf('\\', start)
            if (next == -1) {
                if (start < pattern.length) {
                    result.append(pattern, start, pattern.length)
                }
                break
            }
            if (next == pattern.length - 1) {
                // Return original string.
                return pattern
            }
            when (pattern[next + 1]) {
                ' ', '#', '!' -> {
                    result.append(pattern, start, next)
                    start = next + 1
                }
                else -> return pattern
            }
        }
        return result.toString()
    }

    /**
     * Split pattern with saving slashes.
     *
     * @param pattern Path pattern.
     * @return Path pattern items.
     */
    fun splitPattern(pattern: String): MutableList<String> {
        val result = ArrayList<String>(count(pattern, PATH_SEPARATOR) + 1)
        var start = 0
        while (true) {
            val next: Int = pattern.indexOf(PATH_SEPARATOR, start)
            if (next == -1) {
                if (start < pattern.length) {
                    result.add(pattern.substring(start))
                }
                break
            }
            result.add(pattern.substring(start, next + 1))
            start = next + 1
        }
        return result
    }

    /**
     * Remove redundant pattern parts and make patterns more simple.
     *
     * @param tokens Original modifiable list.
     * @return Return tokens,
     */
    fun normalizePattern(tokens: MutableList<String>): MutableList<String> {
        // By default without slashes using mask for files in all subdirectories
        if (tokens.size == 1 && !tokens[0].contains("/")) {
            tokens.add(0, "**/")
        }
        // Normalized pattern always starts with "/"
        if (tokens.size == 0 || tokens[0] != "/") {
            tokens.add(0, "/")
        }
        // Replace:
        //  * "**/*/" to "*/**/"
        //  * "**/**/" to "**/"
        //  * "**.foo" to "**/*.foo"
        var index = 1
        while (index < tokens.size) {
            val thisToken: String = tokens[index]
            val prevToken: String = tokens[index - 1]
            if ((thisToken == "/")) {
                tokens.removeAt(index)
                continue
            }
            if ((thisToken == "**/") && (prevToken == "**/")) {
                tokens.removeAt(index)
                continue
            }
            if ((thisToken != "**/") && thisToken.startsWith("**")) {
                tokens.add(index, "**/")
                tokens[index + 1] = thisToken.substring(1)
                continue
            }
            if ((thisToken == "*/") && (prevToken == "**/")) {
                tokens[index - 1] = "*/"
                tokens[index] = "**/"
                index--
                continue
            }
            index++
        }
        // Remove tailing "**/" and "*"
        while (tokens.isNotEmpty()) {
            val token: String = tokens[tokens.size - 1]
            if ((token == "**/") || (token == "*")) {
                tokens.removeAt(tokens.size - 1)
            } else {
                break
            }
        }
        return tokens
    }

    fun count(s: String, c: Char): Int {
        var start = 0
        var count = 0
        while (true) {
            start = s.indexOf(c, start)
            if (start == -1) break
            count++
            start++
        }
        return count
    }
}
