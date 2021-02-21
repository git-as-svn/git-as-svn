/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.ldap.config

import com.unboundid.ldap.sdk.BindRequest
import com.unboundid.ldap.sdk.SimpleBindRequest

/**
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 * @see SimpleBindRequest
 */
class LdapBindSimple : LdapBind {
    private var bindDn: String? = null
    private var password: String? = null

    constructor()
    constructor(bindDn: String?, password: String?) {
        this.bindDn = bindDn
        this.password = password
    }

    override fun createBindRequest(): BindRequest {
        return SimpleBindRequest(bindDn, password)
    }
}
