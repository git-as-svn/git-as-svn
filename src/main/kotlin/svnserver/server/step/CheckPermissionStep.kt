/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.step

import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNException
import svnserver.server.SessionContext
import java.io.IOException

/**
 * Step for check permission.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class CheckPermissionStep constructor(private val nextStep: Step, private val checker: Checker?) : Step {
    fun interface Checker {
        @Throws(SVNException::class, IOException::class)
        fun check(context: SessionContext)
    }

    @Throws(IOException::class, SVNException::class)
    override fun process(context: SessionContext) {
        if (checker != null) {
            try {
                checker.check(context)
            } catch (e: SVNException) {
                if (e.errorMessage.errorCode !== SVNErrorCode.RA_NOT_AUTHORIZED) {
                    throw e
                }
                if (!context.user.isAnonymous) {
                    throw e
                }
                context.authenticate(false)
                checker.check(context)
                nextStep.process(context)
                return
            }
        }
        context.writer
            .listBegin()
            .word("success")
            .listBegin()
            .listBegin()
            .listEnd()
            .string("")
            .listEnd()
            .listEnd()
        nextStep.process(context)
    }
}
