/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command

import org.eclipse.jgit.util.Holder
import org.tmatesoft.svn.core.SVNException
import svnserver.parser.SvnServerWriter
import svnserver.repository.Depth
import svnserver.repository.locks.LockDesc
import svnserver.repository.locks.LockStorage
import svnserver.server.SessionContext
import java.io.IOException

/**
 * <pre>
 * get-lock
 * params:    ( path:string )
 * response:  ( [ lock:lockdesc ] )
</pre> *
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class GetLockCmd : BaseCmd<GetLockCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val writer: SvnServerWriter = context.writer
        val path: String = context.getRepositoryPath(args.path)
        val holder: Holder<LockDesc> = context.branch.repository.wrapLockRead { lockStorage: LockStorage ->
            val it: Iterator<LockDesc> = lockStorage.getLocks(context.user, context.branch, context.getRepositoryPath(path), Depth.Empty)
            Holder(if (it.hasNext()) it.next() else null)
        }
        writer.listBegin()
            .word("success")
            .listBegin()
            .listBegin()
        LockCmd.writeLock(writer, holder.get())
        writer
            .listEnd()
            .listEnd()
            .listEnd()
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: Params) {
        context.checkRead(context.getRepositoryPath(args.path))
    }

    class Params(val path: String)
}
