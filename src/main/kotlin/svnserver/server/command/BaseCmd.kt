/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command

import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import svnserver.parser.MessageParser
import svnserver.parser.SvnServerParser
import svnserver.parser.SvnServerWriter
import svnserver.parser.token.ListEndToken
import svnserver.server.SessionContext
import svnserver.server.step.CheckPermissionStep
import java.io.IOException

/**
 * SVN client command base class.
 * Must be stateless and thread-safe.
 *
 * @author a.navrotskiy
 */
abstract class BaseCmd<T> {
    /**
     * Arguments class.
     *
     * @return Arguments class.
     */
    abstract val arguments: Class<out T>

    @Throws(IOException::class, SVNException::class)
    protected open fun process(context: SessionContext, args: T) {
        context.push(CheckPermissionStep({ sessionContext: SessionContext -> processCommand(sessionContext, args) }, { sessionContext: SessionContext -> permissionCheck(sessionContext, args) }))
    }

    @Throws(IOException::class, SVNException::class)
    fun process(context: SessionContext, parser: SvnServerParser) {
        val param: T = MessageParser.parse(arguments, parser)
        parser.readToken(ListEndToken::class.java)
        process(context, param)
    }

    /**
     * Process command.
     *
     * @param context Session context.
     * @param args    Command arguments.
     */
    @Throws(IOException::class, SVNException::class)
    protected abstract fun processCommand(context: SessionContext, args: T)

    /**
     * Check permissions for this command.
     *
     * @param context Session context.
     * @param args    Command arguments.
     */
    @Throws(IOException::class, SVNException::class)
    protected abstract fun permissionCheck(context: SessionContext, args: T)

    @Throws(IOException::class, SVNException::class)
    fun defaultPermissionCheck(context: SessionContext) {
        context.checkRead(context.getRepositoryPath(""))
    }

    protected fun getRevision(rev: IntArray, defaultRevision: Int): Int {
        if (rev.isNotEmpty()) {
            val revNum: Int = rev[0]
            if (revNum >= 0) return revNum
        }
        return defaultRevision
    }

    fun getRevisionOrLatest(rev: IntArray, context: SessionContext): Int {
        if (rev.isNotEmpty()) {
            val revNum: Int = rev[0]
            if (revNum >= 0) return revNum
        }
        return context.branch.latestRevision.id
    }

    companion object {
        @Throws(IOException::class)
        fun sendError(writer: SvnServerWriter, errorMessage: SVNErrorMessage) {
            writer
                .listBegin()
                .word("failure")
                .listBegin()
                .listBegin()
                .number(errorMessage.errorCode.code.toLong())
                .string(errorMessage.messageTemplate)
                .string("")
                .number(0)
                .listEnd()
                .listEnd()
                .listEnd()
        }
    }
}
