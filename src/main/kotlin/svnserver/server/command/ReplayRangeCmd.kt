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
import svnserver.parser.SvnServerWriter
import svnserver.repository.git.GitRevision
import svnserver.server.SessionContext
import java.io.IOException

/**
 * Send revisions as is.
 *
 *
 * <pre>
 * replay-range
 * params:    ( start-rev:number end-rev:number low-water-mark:number
 * send-deltas:bool )
 * After auth exchange completes, server sends each revision
 * from start-rev to end-rev, alternating between sending 'revprops'
 * entries and sending the revision in the editor command set.
 * After all revisions are complete, server sends response.
 * revprops:  ( revprops:word props:proplist )
 * (revprops here is the literal word "revprops".)
 * response   ( )
</pre> *
 *
 * @author a.navrotskiy
 */
class ReplayRangeCmd : BaseCmd<ReplayRangeCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        if (args.startRev > args.endRev) {
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Invalid revision range: start: " + args.startRev + ", end " + args.endRev))
        }
        val writer: SvnServerWriter = context.writer
        for (revision in args.startRev..args.endRev) {
            val revisionInfo: GitRevision = context.branch.getRevisionInfo(revision)
            writer
                .listBegin()
                .word("revprops")
                .writeMap(revisionInfo.getProperties(true))
                .listEnd()
            ReplayCmd.replayRevision(context, revision, args.lowRevision, args.sendDeltas)
        }
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

    class Params(val startRev: Int, val endRev: Int, val lowRevision: Int, val sendDeltas: Boolean)
}
