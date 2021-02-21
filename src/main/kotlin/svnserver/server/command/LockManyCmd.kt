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
 * lock-many
 * params:    ( [ comment:string ] steal-lock:bool ( ( path:string
 * [ current-rev:number ] ) ... ) )
 * Before sending response, server sends lock cmd status and descriptions,
 * ending with "done".
 * lock-info: ( success ( lock:lockdesc ) ) | ( failure ( err:error ) )
 * | done
 * response: ( )
</pre> *
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class LockManyCmd : BaseCmd<LockManyCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val writer: SvnServerWriter = context.writer
        val latestRev: Int = context.branch.latestRevision.id
        val comment: String? = if (args.comment.isEmpty()) null else args.comment[0]
        val targets = args.paths.map { pathRev ->
            val path: String = context.getRepositoryPath(pathRev.path)
            val rev: Int = getRevision(pathRev.rev, latestRev)
            LockTarget(path, rev)
        }.toTypedArray()
        val locks: Array<LockDesc>
        try {
            locks = context.branch.repository.wrapLockWrite { lockStorage: LockStorage ->
                try {
                    return@wrapLockWrite lockStorage.lock(context.user, context.branch, comment, args.stealLock, targets)
                } catch (e: LockConflictException) {
                    throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_PATH_ALREADY_LOCKED, String.format("Path is already locked: %s", e.lock.path)))
                }
            }
            for (lock: LockDesc? in locks) {
                writer.listBegin().word("success")
                LockCmd.writeLock(writer, lock)
                writer.listEnd()
            }
        } catch (e: SVNException) {
            sendError(writer, e.errorMessage)
        }
        writer.word("done")
        writer.listBegin()
            .word("success")
            .listBegin()
            .listEnd()
            .listEnd()
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: Params) {
        for (pathRev: PathRev in args.paths) context.checkWrite(context.getRepositoryPath(pathRev.path))
    }

    class PathRev constructor(val path: String, val rev: IntArray)
    class Params constructor(val comment: Array<String>, val stealLock: Boolean, val paths: Array<PathRev>)
}
