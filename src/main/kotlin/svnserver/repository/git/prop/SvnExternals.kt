/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop

import org.eclipse.jgit.lib.FileMode
import svnserver.repository.git.RepositoryFormat
import java.io.IOException
import java.io.InputStream

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
internal data class SvnExternals(private val value: String) : GitProperty {

    override fun apply(props: MutableMap<String, String>) {
        props["svn:externals"] = value
    }

    override val filterName: String?
        get() {
            return null
        }

    override fun createForChild(name: String, mode: FileMode): GitProperty? {
        return null
    }
}

class SvnExternalsFactory : GitPropertyFactory {
    @Throws(IOException::class)
    override fun create(stream: InputStream, format: RepositoryFormat, stringInterner: (String) -> String): Array<GitProperty> {
        val value = stringInterner(stream.bufferedReader().use { it.readText() })
        return arrayOf(SvnExternals(value))
    }
}
