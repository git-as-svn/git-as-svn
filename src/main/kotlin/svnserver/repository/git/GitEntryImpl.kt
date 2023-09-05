/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import org.eclipse.jgit.lib.FileMode
import svnserver.StringHelper
import svnserver.repository.git.prop.GitProperty
import java.io.IOException

/**
 * Simple GitEntry implementation.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
internal open class GitEntryImpl(parentProps: Array<GitProperty>, parentPath: String, props: Array<GitProperty>, final override val fileName: String, fileMode: FileMode, stringInterner: (String) -> String) : GitEntry {
    final override val fullPath: String
    final override val rawProperties: Array<GitProperty> = GitProperty.joinProperties(parentProps, fileName, fileMode, props)

    init {
        fullPath = stringInterner(StringHelper.joinPath(parentPath, fileName))
    }

    override fun createChild(name: String, isDir: Boolean, stringInterner: (String) -> String): GitEntry {
        return GitEntryImpl(rawProperties, fullPath, GitProperty.emptyArray, name, if (isDir) FileMode.TREE else FileMode.REGULAR_FILE, stringInterner)
    }

    @Throws(IOException::class)
    override fun getEntry(name: String, stringInterner: (String) -> String): GitFile? {
        return null
    }
}
