/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop

import org.eclipse.jgit.lib.FileMode
import org.ini4j.Ini
import org.ini4j.Profile
import java.io.IOException
import java.io.InputStream
import java.util.*

/**
 * Parse and processing .tgitconfig.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal class GitTortoise private constructor(private val tortoiseProps: Map<String, String>) : GitProperty {

    override fun apply(props: MutableMap<String, String>) {
        props.putAll(tortoiseProps)
    }

    override val filterName: String?
        get() {
            return null
        }

    override fun createForChild(name: String, mode: FileMode): GitProperty? {
        return null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that: GitTortoise = other as GitTortoise
        return (tortoiseProps == that.tortoiseProps)
    }

    override fun hashCode(): Int {
        return tortoiseProps.hashCode()
    }

    companion object {
        @Throws(IOException::class)
        fun parseConfig(stream: InputStream): GitTortoise {
            val ini = Ini(stream)
            val result: MutableMap<String, String> = HashMap()
            for (sectionEntry: Map.Entry<String, Profile.Section> in ini.entries) {
                for (configEntry: Map.Entry<String, String> in sectionEntry.value.entries) {
                    var value: String = configEntry.value
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length - 1)
                    }
                    result[sectionEntry.key + ":" + configEntry.key] = value
                }
            }
            return GitTortoise(if (result.isEmpty()) emptyMap() else result)
        }
    }
}
