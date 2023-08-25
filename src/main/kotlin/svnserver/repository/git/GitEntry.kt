/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import svnserver.repository.git.prop.GitProperty
import java.io.IOException

/**
 * Git entry.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
interface GitEntry {
    val rawProperties: Array<GitProperty>
    val fileName: String
    val fullPath: String
    fun createChild(name: String, isDir: Boolean, stringInterner: (String) -> String): GitEntry

    @Throws(IOException::class)
    fun getEntry(name: String, stringInterner: (String) -> String): GitFile?
}
