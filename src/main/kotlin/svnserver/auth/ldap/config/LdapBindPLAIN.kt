/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.ldap.config

import com.unboundid.ldap.sdk.BindRequest
import com.unboundid.ldap.sdk.PLAINBindRequest

/**
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 * @see PLAINBindRequest
 */
class LdapBindPLAIN private constructor(private var authenticationID: String, private var authorizationID: String?, private var password: String) : LdapBind {
    @JvmOverloads
    constructor(authenticationID: String = "", password: String = "") : this(authenticationID, null, password)

    override fun createBindRequest(): BindRequest {
        return PLAINBindRequest(authenticationID, authorizationID, password)
    }
}
