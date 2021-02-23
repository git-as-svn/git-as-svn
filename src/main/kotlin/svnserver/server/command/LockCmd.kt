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
import svnserver.repository.locks.LockDesc
import svnserver.repository.locks.LockStorage
import svnserver.repository.locks.LockTarget
import svnserver.server.SessionContext
import java.io.IOException

/**
 * <pre>
 * lock
 * params:    ( path:string [ comment:string ] steal-lock:bool
 * [ current-rev:number ] )
 * response:  ( lock:lockdesc )
</pre> *
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class LockCmd : BaseCmd<LockCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val rev: Int = getRevisionOrLatest(args.rev, context)
        val path: String = context.getRepositoryPath(args.path)
        val lockTarget = LockTarget(path, rev)
        val comment: String? = if (args.comment.isEmpty()) null else args.comment[0]
        val lockDescs: Array<LockDesc> = context.branch.repository.wrapLockWrite { lockStorage: LockStorage ->
            try {
                return@wrapLockWrite lockStorage.lock(context.user, context.branch, comment, args.stealLock, arrayOf(lockTarget))
            } catch (e: LockConflictException) {
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_PATH_ALREADY_LOCKED, String.format("Path is already locked: %s", e.lock.path)))
            }
        }
        if (lockDescs.size != 1) {
            throw IllegalStateException()
        }
        val writer: SvnServerWriter = context.writer
        writer.listBegin()
            .word("success")
            .listBegin()
        writeLock(writer, lockDescs[0])
        writer
            .listEnd()
            .listEnd()
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: Params) {
        context.checkWrite(context.getRepositoryPath(args.path))
    }

    class Params constructor(val path: String, val comment: Array<String>, val stealLock: Boolean, val rev: IntArray)
    companion object {
        @Throws(IOException::class)
        fun writeLock(writer: SvnServerWriter, lockDesc: LockDesc?) {
            if (lockDesc != null) writer
                .listBegin()
                .string(lockDesc.path)
                .string(lockDesc.token)
                .string(lockDesc.owner ?: "")
                .stringNullable(lockDesc.comment)
                .string(lockDesc.createdString)
                .listBegin()
                .listEnd()
                .listEnd()
        }
    }
}
