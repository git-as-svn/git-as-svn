/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.msg

import org.tmatesoft.svn.core.SVNURL

/**
 * Информация о подключенном клиенте.
 * <pre>
 * response: ( version:number ( cap:word ... ) url:string
 * ? ra-client:string ( ? client:string ) )
</pre> *
 *
 * @author a.navrotskiy
 */
class ClientInfo constructor(val protocolVersion: Int, val capabilities: Array<String>, url: String, val raClient: String) {
    val url: SVNURL = SVNURL.parseURIEncoded(url)
}
