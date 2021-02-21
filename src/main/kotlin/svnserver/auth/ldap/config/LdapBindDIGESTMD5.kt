/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.ldap.config

import com.unboundid.ldap.sdk.BindRequest
import com.unboundid.ldap.sdk.DIGESTMD5BindRequest

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 * @see DIGESTMD5BindRequest
 */
class LdapBindDIGESTMD5 private constructor(private var authenticationID: String, private var authorizationID: String?, private var password: String, private var realm: String?) : LdapBind {
    constructor() : this("", null, "", null)

    override fun createBindRequest(): BindRequest {
        return DIGESTMD5BindRequest(authenticationID, authorizationID, password, realm)
    }
}
