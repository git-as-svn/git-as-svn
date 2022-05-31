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
import java.io.IOException
import java.io.InputStream

/**
 * Parse and processing .tgitconfig.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal data class GitTortoise private constructor(private val tortoiseProps: Map<String, String>) : GitProperty {

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

    companion object {
        @Throws(IOException::class)
        fun parseConfig(stream: InputStream): GitTortoise {
            val ini = Ini(stream)
            val result = HashMap<String, String>()
            for (sectionEntry in ini.entries) {
                for (configEntry in sectionEntry.value.entries) {
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
