/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config

import svnserver.context.SharedContext
import svnserver.repository.git.GitSubmodules
import java.io.IOException
import java.util.*

/**
 * Submodules configuration list
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class SharedSubmodulesConfig : ArrayList<String>(), SharedConfig {
    @Throws(IOException::class)
    override fun create(context: SharedContext) {
        context.add(GitSubmodules::class.java, GitSubmodules(context.basePath, this))
    }
}
