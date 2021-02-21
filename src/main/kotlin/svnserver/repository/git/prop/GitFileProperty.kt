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
import java.util.*

/**
 * Parse and processing .gitignore.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal class GitFileProperty
/**
 * Set property to all matched file.
 *
 * @param matcher  File matcher.
 * @param property Property name.
 * @param value    Property value.
 */ constructor(private val matcher: PathMatcher, private val property: String, private val value: String?) : GitProperty {
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
                return GitFileProperty(matcherChild, property, value)
            } else if (matcherChild.isMatch) {
                return object : GitProperty {
                    override fun apply(props: MutableMap<String, String>) {
                        if (value != null) {
                            props.put(property, value)
                        } else {
                            props.remove(property)
                        }
                    }

                    override val filterName: String?
                        get() {
                            return null
                        }

                    override fun createForChild(name: String, mode: FileMode): GitProperty? {
                        return null
                    }
                }
            }
        }
        return null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that: GitFileProperty = other as GitFileProperty
        return ((matcher == that.matcher) && (property == that.property) && Objects.equals(value, that.value))
    }

    override fun hashCode(): Int {
        var result: Int = matcher.hashCode()
        result = 31 * result + property.hashCode()
        if (value != null) {
            result = 31 * result + value.hashCode()
        }
        return result
    }
}
