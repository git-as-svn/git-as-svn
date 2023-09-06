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
 * Factory for creating .gitignore properties.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitIgnoreFactory : GitPropertyFactory {
    override val fileName: String
        get() {
            return ".gitignore"
        }

    @Throws(IOException::class)
    override fun create(stream: InputStream, format: RepositoryFormat, stringInterner: (String) -> String): Array<GitProperty> {
        return arrayOf(GitIgnore.parseConfig(stream, stringInterner))
    }
}
