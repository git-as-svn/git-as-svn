/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop

import org.eclipse.jgit.errors.InvalidPatternException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.slf4j.Logger
import org.tmatesoft.svn.core.SVNProperty
import svnserver.Loggers
import svnserver.repository.git.path.PathMatcher
import svnserver.repository.git.path.Wildcard
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.regex.PatternSyntaxException

/**
 * Parse and processing .gitignore.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal data class GitIgnore constructor(
    // svn:ignore
    private val local: Array<String>,
    // svn:global-ignores
    private val global: Array<String>,
    private val matchers: Array<PathMatcher>,
) : GitProperty {

    override fun apply(props: MutableMap<String, String>) {
        if (global.isNotEmpty()) {
            props.compute(SVNProperty.INHERITABLE_IGNORES) { _, value -> addIgnore(value, global) }
        }
        if (local.isNotEmpty()) {
            props.compute(SVNProperty.IGNORE) { _, value -> addIgnore(value, local) }
        }
    }

    override fun createForChild(name: String, mode: FileMode): GitProperty? {
        if (matchers.isEmpty() || (mode.objectType == Constants.OBJ_BLOB)) {
            return null
        }
        val localList = ArrayList<String>()
        val globalList = ArrayList<String>()
        val childMatchers: MutableList<PathMatcher> = ArrayList()
        for (matcher: PathMatcher in matchers) {
            processMatcher(localList, globalList, childMatchers, matcher.createChild(name, true))
        }
        if (localList.isEmpty() && globalList.isEmpty() && childMatchers.isEmpty()) {
            return null
        }
        return GitIgnore(localList.toTypedArray(), globalList.toTypedArray(), childMatchers.toTypedArray())
    }

    override val filterName: String?
        get() {
            return null
        }

    override fun hashCode(): Int {
        var result = local.contentHashCode()
        result = 31 * result + global.contentHashCode()
        result = 31 * result + matchers.contentHashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GitIgnore

        if (!local.contentEquals(other.local)) return false
        if (!global.contentEquals(other.global)) return false
        if (!matchers.contentEquals(other.matchers)) return false

        return true
    }

    companion object {
        private val log: Logger = Loggers.git
        private fun processMatcher(local: MutableList<String>, global: MutableList<String>, matchers: MutableList<PathMatcher>, matcher: PathMatcher?) {
            if (matcher == null) {
                return
            }
            val maskGlobal: String? = matcher.svnMaskGlobal
            if (maskGlobal != null) {
                global.add(maskGlobal)
                return
            }
            val maskLocal: String? = matcher.svnMaskLocal
            if (maskLocal != null) {
                local.add(maskLocal)
            }
            matchers.add(matcher)
        }

        /**
         * Parse and store .gitignore data (http://git-scm.com/docs/gitignore).
         *
         *
         * Important:
         * * An optional prefix "!" which negates the pattern is not supported.
         * * Mask trailing slash is not supported (/foo/bar/ works like /foo/bar).
         *
         * @param stream Original file content.
         */
        @Throws(IOException::class)
        fun parseConfig(stream: InputStream): GitIgnore {
            val reader = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))

            val localList = ArrayList<String>()
            val globalList = ArrayList<String>()
            val matchers = ArrayList<PathMatcher>()
            for (txt in reader.lines()) {
                val line = trimLine(txt)
                if (line.isEmpty()) continue
                try {
                    val wildcard = Wildcard(line)
                    if (wildcard.isSvnCompatible) {
                        processMatcher(localList, globalList, matchers, wildcard.matcher)
                    }
                } catch (e: InvalidPatternException) {
                    log.warn("Found invalid git pattern: {}", line)
                } catch (e: PatternSyntaxException) {
                    log.warn("Found invalid git pattern: {}", line)
                }
            }

            return GitIgnore(localList.toTypedArray(), globalList.toTypedArray(), matchers.toTypedArray())
        }

        private fun trimLine(line: String): String {
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!") || line.startsWith("\\!")) return ""
            // Remove trailing spaces end escapes.
            var end: Int = line.length
            while (end > 0) {
                val c: Char = line[end - 1]
                if (c != ' ') {
                    if ((end < line.length) && (line[end - 1] == '\\')) {
                        end++
                    }
                    break
                }
                end--
            }
            return line.substring(0, end)
        }

        private fun addIgnore(oldValue: String?, ignores: Array<String>): String {
            val contains = HashSet<String>()
            val result = StringBuilder()
            if (oldValue != null) {
                result.append(oldValue)
                contains.addAll(oldValue.split("\n"))
            }
            for (ignore in ignores) {
                if (contains.add(ignore)) {
                    result.append(ignore).append('\n')
                }
            }
            return result.toString()
        }
    }
}
