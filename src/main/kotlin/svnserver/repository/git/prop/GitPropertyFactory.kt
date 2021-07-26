/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop

import svnserver.repository.git.RepositoryFormat
import java.io.IOException
import java.io.InputStream

/**
 * Factory for creating GitProperty.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
interface GitPropertyFactory {
    /**
     * Return mapped git file name.
     *
     * @return File name.
     */
    val fileName: String

    /**
     * Create git property worker by file content.
     *
     * @param stream File content.
     * @return Git property workers.
     */
    @Throws(IOException::class)
    fun create(stream: InputStream, format: RepositoryFormat): Array<GitProperty>
}
