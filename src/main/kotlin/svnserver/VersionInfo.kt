/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver

import java.io.IOException
import java.util.*

/**
 * Version information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class VersionInfo private constructor() {
    private var revision: String? = null
    private var tag: String? = null

    companion object {
        private val s_instance: VersionInfo = VersionInfo()
        val versionInfo: String
            get() {
                if (s_instance.revision == null) return "none version info"
                if (s_instance.tag == null) return s_instance.revision!!
                return s_instance.tag + ", " + s_instance.revision
            }

        private fun getProperty(props: Properties, name: String, defaultValue: String?): String {
            val value: String? = props.getProperty(name)
            return if (value != null && !value.startsWith("\${")) value else (defaultValue)!!
        }
    }

    init {
        try {
            javaClass.getResourceAsStream("VersionInfo.properties").use { stream ->
                if (stream == null) {
                    throw IllegalStateException()
                }
                val props = Properties()
                props.load(stream)
                revision = getProperty(props, "revision", null)
                tag = getProperty(props, "tag", null)
            }
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
    }
}
