/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command

import org.tmatesoft.svn.core.SVNException
import svnserver.server.SessionContext
import java.io.IOException

/**
 * Simple lambda command.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class LambdaCmd<T> internal constructor(override val arguments: Class<T>, private val callback: Callback<T>) : BaseCmd<T>() {
    @Throws(IOException::class, SVNException::class)
    public override fun process(context: SessionContext, args: T) {
        callback.processCommand(context, args)
    }

    override fun processCommand(context: SessionContext, args: T) {}

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: T) {
        defaultPermissionCheck(context)
    }

    fun interface Callback<T> {
        @Throws(IOException::class, SVNException::class)
        fun processCommand(sessionContext: SessionContext, args: T)
    }
}
