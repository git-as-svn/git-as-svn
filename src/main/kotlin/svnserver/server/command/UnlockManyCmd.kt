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
 * unlock-many
 * params:    ( break-lock:bool ( ( path:string [ token:string ] ) ... ) )
 * Before sending response, server sends unlocked paths, ending with "done".
 * pre-response: ( success ( path:string ) ) | ( failure ( err:error ) )
 * | done
 * response:  ( )
</pre> *
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class UnlockManyCmd : BaseCmd<UnlockManyCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val writer: SvnServerWriter = context.writer
        val targets = args.paths.map { pathToken ->
            val path: String = context.getRepositoryPath(pathToken.path)
            val lockToken: String? = if (pathToken.lockToken.isEmpty()) null else pathToken.lockToken[0]
            UnlockTarget(context.getRepositoryPath(path), lockToken)
        }.toTypedArray()
        try {
            context.branch.repository.wrapLockWrite { lockStorage: LockStorage ->
                try {
                    lockStorage.unlock(context.user, context.branch, args.breakLock, targets)
                } catch (e: LockConflictException) {
                    throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_LOCK, e.lock.path))
                }
                true
            }
            for (path: PathToken in args.paths) writer
                .listBegin()
                .word("success")
                .listBegin()
                .string(path.path)
                .listEnd()
                .listEnd()
        } catch (e: SVNException) {
            sendError(writer, e.errorMessage)
        }
        writer.word("done")
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .listEnd()
            .listEnd()
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: Params) {
        for (pathRev: PathToken in args.paths) context.checkWrite(context.getRepositoryPath(pathRev.path))
    }

    class PathToken constructor(val path: String, val lockToken: Array<String>)
    class Params constructor(val breakLock: Boolean, val paths: Array<PathToken>)
}
