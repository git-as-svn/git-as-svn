/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth

import org.tmatesoft.svn.core.SVNException
import svnserver.context.Shared

/**
 * User storage.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
interface UserDB : Shared {
    fun authenticators(): Collection<Authenticator>

    @Throws(SVNException::class)
    fun check(username: String, password: String): User?

    @Throws(SVNException::class)
    fun lookupByUserName(username: String): User?

    @Throws(SVNException::class)
    fun lookupByExternal(external: String): User?
}
