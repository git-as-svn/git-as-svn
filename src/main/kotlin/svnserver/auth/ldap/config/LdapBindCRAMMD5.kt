/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.ldap.config

import com.unboundid.ldap.sdk.BindRequest
import com.unboundid.ldap.sdk.CRAMMD5BindRequest

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 * @see CRAMMD5BindRequest
 */
class LdapBindCRAMMD5 private constructor(private var authenticationID: String, private var password: String) : LdapBind {
    constructor() : this("", "")

    override fun createBindRequest(): BindRequest {
        return CRAMMD5BindRequest(authenticationID, password)
    }
}
