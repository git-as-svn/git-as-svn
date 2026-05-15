/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop

import org.eclipse.jgit.lib.FileMode
import java.util.concurrent.ConcurrentHashMap

@ConsistentCopyVisibility
internal data class SimpleFileProperty private constructor(private val property: String, private val value: String?) : GitProperty {
    override fun apply(props: MutableMap<String, String>) {
        if (value != null) {
            props[property] = value
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

    companion object {
        // Without this cache, we have 3 *millions* of instances on at-git.mail.msk
        private val cache = ConcurrentHashMap<SimpleFileProperty, SimpleFileProperty>()

        fun create(property: String, value: String?): SimpleFileProperty {
            val result = SimpleFileProperty(property, value)
            return cache.computeIfAbsent(result) { result }
        }
    }
}
