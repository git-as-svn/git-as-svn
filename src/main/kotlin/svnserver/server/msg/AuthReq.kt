/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.msg

/**
 * Message from client with authentication type.
 *
 *
 * auth-response: ( mech:word [ token:string ] )
 *
 * @author a.navrotskiy
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class AuthReq constructor(val mech: String, private val token: Array<String>) {
    fun getToken(): String {
        return if (token.isEmpty()) "" else token[0]
    }
}
