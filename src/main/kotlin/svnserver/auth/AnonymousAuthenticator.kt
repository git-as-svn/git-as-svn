/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth

import svnserver.server.SessionContext

/**
 * Anonymous authentication.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class AnonymousAuthenticator private constructor() : Authenticator {
    override val methodName: String
        get() {
            return "ANONYMOUS"
        }

    override fun authenticate(context: SessionContext, token: String): User {
        return User.anonymous
    }

    companion object {
        private val instance: AnonymousAuthenticator = AnonymousAuthenticator()
        fun get(): AnonymousAuthenticator {
            return instance
        }
    }
}
