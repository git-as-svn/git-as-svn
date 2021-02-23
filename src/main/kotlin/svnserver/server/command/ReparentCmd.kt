/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command

import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNURL
import svnserver.parser.SvnServerWriter
import svnserver.server.SessionContext
import java.io.IOException

/**
 * Change current path in repository.
 *
 *
 * <pre>
 * reparent
 * params:   ( url:string )
 * response: ( )
</pre> *
 *
 * @author a.navrotskiy
 */
class ReparentCmd : BaseCmd<ReparentCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        context.setParent(args.url)
        val writer: SvnServerWriter = context.writer
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .listEnd()
            .listEnd()
    }

    override fun permissionCheck(context: SessionContext, args: Params) {
        // noop
    }

    class Params constructor(url: String) {
        val url: SVNURL = SVNURL.parseURIEncoded(url)
    }
}
