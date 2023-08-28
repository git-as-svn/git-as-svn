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
import svnserver.repository.git.GitRevision
import svnserver.server.SessionContext
import java.io.IOException

/**
 * Get revision property.
 *
 *
 * <pre>
 * rev-prop
 * params:   ( rev:number name:string )
 * response: ( [ value:string ] )
</pre> *
 *
 * @author a.navrotskiy
 */
class RevPropCmd : BaseCmd<RevPropCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val writer: SvnServerWriter = context.writer
        val revision: GitRevision = context.branch.getRevisionInfo(args.revision)
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .listBegin()
        val propValue: String? = revision.getProperties(true)[args.propName]
        if (propValue != null) {
            writer.string(propValue)
        }
        writer
            .listEnd()
            .listEnd()
            .listEnd()
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: Params) {
        defaultPermissionCheck(context)
    }

    class Params(val revision: Int, val propName: String)
}
