/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command

import org.tmatesoft.svn.core.SVNException
import svnserver.parser.SvnServerWriter
import svnserver.server.SessionContext
import java.io.IOException

/**
 * Change current path in repository.
 *
 * <pre>
 * get-latest-rev
 * params:   ( )
 * response: ( rev:number )
</pre> *
 *
 * @author a.navrotskiy
 */
class GetLatestRevCmd : BaseCmd<NoParams>() {
    override val arguments: Class<out NoParams>
        get() {
            return NoParams::class.java
        }

    @Throws(IOException::class)
    override fun processCommand(context: SessionContext, args: NoParams) {
        val writer: SvnServerWriter = context.writer
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .number(context.branch.latestRevision.id.toLong())
            .listEnd()
            .listEnd()
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: NoParams) {
        defaultPermissionCheck(context)
    }
}
