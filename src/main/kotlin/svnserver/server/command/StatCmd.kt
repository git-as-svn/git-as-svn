/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command

import org.tmatesoft.svn.core.SVNException
import svnserver.repository.git.GitFile
import svnserver.server.SessionContext
import java.io.IOException

/**
 * <pre>
 * stat
 * params:   ( path:string [ rev:number ] )
 * response: ( ? entry:dirent )
 * dirent:   ( kind:node-kind size:number has-props:bool
 * created-rev:number [ created-date:string ]
 * [ last-author:string ] )
 * New in svn 1.2.  If path is non-existent, an empty response is returned.
</pre> *
 *
 * @author a.navrotskiy
 */
class StatCmd : BaseCmd<StatCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val revision: Int = getRevisionOrLatest(args.rev, context)
        val file: GitFile? = context.getFile(revision, args.path)
        context.writer
            .listBegin()
            .word("success")
            .listBegin()
            .listBegin()
        if (file != null) {
            context.writer
                .listBegin()
                .word(file.kind.toString()) // kind
                .number(file.size) // size
                .bool(file.properties.isNotEmpty()) // has properties
                .number(file.lastChange.id.toLong()) // last change revision
                .stringNullable(file.lastChange.dateString) // last change date
                .stringNullable(file.lastChange.author) // last change author
                .listEnd()
        }
        context.writer
            .listEnd()
            .listEnd()
            .listEnd()
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: Params) {
        context.checkRead(context.getRepositoryPath(args.path))
    }

    class Params constructor(val path: String, val rev: IntArray)
}
