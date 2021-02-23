/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command

import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import ru.bozaro.gitlfs.common.LockConflictException
import svnserver.parser.SvnServerWriter
import svnserver.repository.locks.LockStorage
import svnserver.repository.locks.UnlockTarget
import svnserver.server.SessionContext
import java.io.IOException

/**
 * <pre>
 * unlock
 * params:    ( path:string [ token:string ] break-lock:bool )
 * response:  ( )
</pre> *
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class UnlockCmd : BaseCmd<UnlockCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val path: String = context.getRepositoryPath(args.path)
        val lockToken: String? = if (args.lockToken.isEmpty()) null else args.lockToken[0]
        context.branch.repository.wrapLockWrite { lockStorage: LockStorage ->
            try {
                lockStorage.unlock(context.user, context.branch, args.breakLock, arrayOf(UnlockTarget(context.getRepositoryPath(path), lockToken)))
            } catch (e: LockConflictException) {
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_BAD_LOCK_TOKEN, e.lock.path))
            }
            true
        }
        val writer: SvnServerWriter = context.writer
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .listEnd()
            .listEnd()
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: Params) {
        context.checkWrite(context.getRepositoryPath(args.path))
    }

    class Params constructor(val path: String, val lockToken: Array<String>, val breakLock: Boolean)
}
