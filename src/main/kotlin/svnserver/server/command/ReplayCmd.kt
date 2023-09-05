/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command

import org.tmatesoft.svn.core.SVNDepth
import org.tmatesoft.svn.core.SVNException
import svnserver.parser.SvnServerWriter
import svnserver.repository.Depth
import svnserver.repository.SendCopyFrom
import svnserver.server.SessionContext
import svnserver.server.command.DeltaCmd.ReportPipeline
import java.io.IOException

/**
 * Send revision as is.
 *
 *
 * <pre>
 * replay
 * params:    ( revision:number low-water-mark:number send-deltas:bool )
 * After auth exchange completes, server switches to editor command set.
 * After edit completes, server sends response.
 * response   ( )
</pre> *
 *
 * @author a.navrotskiy
 */
class ReplayCmd : BaseCmd<ReplayCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        replayRevision(context, args.revision, args.lowRevision, args.sendDeltas)
        val writer: SvnServerWriter = context.writer
        writer
            .listBegin()
            .word("success")
            .listBegin().listEnd()
            .listEnd()
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: Params) {
        defaultPermissionCheck(context)
    }

    class Params(val revision: Int, val lowRevision: Int, val sendDeltas: Boolean)
    companion object {
        @Throws(IOException::class, SVNException::class)
        fun replayRevision(context: SessionContext, revision: Int, lowRevision: Int, sendDeltas: Boolean) {
            val pipeline = ReportPipeline(
                context,
                DeltaParams(
                    intArrayOf(revision),
                    "",
                    "",
                    sendDeltas,
                    Depth.Infinity,
                    SendCopyFrom.OnlyRelative,
                    ignoreAncestry = false,
                    includeInternalProps = false,
                    lowRevision = lowRevision
                )
            )
            pipeline.setPathReport("", revision - 1, false, SVNDepth.INFINITY)
            pipeline.sendDelta(context)
            val writer: SvnServerWriter = context.writer
            writer
                .listBegin()
                .word("finish-replay")
                .listBegin().listEnd()
                .listEnd()
        }
    }
}
