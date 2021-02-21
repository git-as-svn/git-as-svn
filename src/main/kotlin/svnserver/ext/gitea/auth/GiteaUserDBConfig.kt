/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.auth

import svnserver.auth.UserDB
import svnserver.config.UserDBConfig
import svnserver.context.SharedContext

/**
 * GitLab authentication configuration.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Andrew Thornton <zeripath@users.noreply.github.com>
 */
class GiteaUserDBConfig : UserDBConfig {
    override fun create(context: SharedContext): UserDB {
        return GiteaUserDB(context)
    }
}
