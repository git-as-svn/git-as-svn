/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository

import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import svnserver.auth.User
import svnserver.context.Local
import java.io.IOException

/**
 * Repository access checker.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
interface VcsAccess : Local {
    @Throws(IOException::class, SVNException::class)
    fun checkRead(user: User, branch: String, path: String) {
        if (!canRead(user, branch, path)) throw SVNException(SVNErrorMessage.create(if (user.isAnonymous) SVNErrorCode.RA_NOT_AUTHORIZED else SVNErrorCode.AUTHZ_UNREADABLE))
    }

    @Throws(IOException::class)
    fun canRead(user: User, branch: String, path: String): Boolean

    @Throws(IOException::class, SVNException::class)
    fun checkWrite(user: User, branch: String, path: String) {
        if (user.isAnonymous) throw SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED))
        if (!canWrite(user, branch, path)) throw SVNException(SVNErrorMessage.create(SVNErrorCode.AUTHZ_UNWRITABLE))
    }

    @Throws(IOException::class)
    fun canWrite(user: User, branch: String, path: String): Boolean

    @Throws(IOException::class)
    fun updateEnvironment(environment: MutableMap<String, String>, user: User) {
    }
}
