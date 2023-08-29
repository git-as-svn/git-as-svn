/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.keys

import org.tmatesoft.svn.core.SVNException
import svnserver.auth.Authenticator
import svnserver.auth.User
import svnserver.auth.UserDB
import java.util.*

class KeyUserDB(private val internal: UserDB, secretToken: String) : UserDB {
    private val keyAuthenticator: KeyAuthenticator = KeyAuthenticator(internal, secretToken)

    override val authenticators: Collection<Authenticator>
        get() {
            val authenticators = ArrayList(internal.authenticators)
            authenticators.add(keyAuthenticator)
            return Collections.unmodifiableList(authenticators)
        }

    @Throws(SVNException::class)
    override fun check(username: String, password: String): User? {
        return internal.check(username, password)
    }

    @Throws(SVNException::class)
    override fun lookupByUserName(username: String): User? {
        return internal.lookupByUserName(username)
    }

    @Throws(SVNException::class)
    override fun lookupByExternal(external: String): User? {
        return internal.lookupByExternal(external)
    }
}
