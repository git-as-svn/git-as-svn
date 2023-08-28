/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command

import org.tmatesoft.svn.core.SVNException
import ru.bozaro.gitlfs.common.JsonHelper
import svnserver.parser.SvnServerWriter
import svnserver.server.SessionContext
import java.io.IOException
import java.text.ParseException
import java.util.*

/**
 * Change current path in repository.
 *
 * <pre>
 * get-dated-rev
 * params:   ( date:string )
 * response: ( rev:number )
</pre> *
 *
 * @author a.navrotskiy
 */
class GetDatedRevCmd : BaseCmd<GetDatedRevCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val writer: SvnServerWriter = context.writer
        val dateTime: Date
        try {
            dateTime = JsonHelper.dateFormat.parse(args.date)
        } catch (e: ParseException) {
            throw IOException(e)
        }
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .number(context.branch.getRevisionByDate(dateTime.time).id.toLong())
            .listEnd()
            .listEnd()
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: Params) {
        defaultPermissionCheck(context)
    }

    class Params(val date: String)
}
