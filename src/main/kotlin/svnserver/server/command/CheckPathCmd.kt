/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command

import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNNodeKind
import svnserver.parser.SvnServerWriter
import svnserver.repository.git.GitFile
import svnserver.server.SessionContext
import java.io.IOException

/**
 * <pre>
 * check-path
 * params:   ( path:string [ rev:number ] )
 * response: ( kind:node-kind )
 * If path is non-existent, 'svn_node_none' kind is returned.
</pre> *
 *
 * @author a.navrotskiy
 */
class CheckPathCmd : BaseCmd<CheckPathCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val revision: Int = getRevisionOrLatest(args.rev, context)
        val file: GitFile? = context.getFile(revision, args.path)
        val kind: SVNNodeKind = file?.kind ?: SVNNodeKind.NONE
        val writer: SvnServerWriter = context.writer
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .word(kind.toString()) // kind
            .listEnd()
            .listEnd()
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: Params) {
        context.checkRead(context.getRepositoryPath(args.path))
    }

    class Params(val path: String, val rev: IntArray)
}
