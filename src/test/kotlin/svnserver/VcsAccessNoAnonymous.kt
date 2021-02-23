/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver

import svnserver.auth.User
import svnserver.repository.VcsAccess

/**
 * Non-anonymous user.
 *
 * @author Artem V. Navrotskiy
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class VcsAccessNoAnonymous : VcsAccess {
    override fun canRead(user: User, branch: String, path: String): Boolean {
        return !user.isAnonymous
    }

    override fun canWrite(user: User, branch: String, path: String): Boolean {
        return canRead(user, branch, path)
    }

    companion object {
        val instance: VcsAccess = VcsAccessNoAnonymous()
    }
}
