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
import svnserver.repository.VcsCopyFrom
import svnserver.server.SessionContext
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/**
 * <pre>
 * get-location-segments
 * params:   ( path:string [ start-rev:number ] [ end-rev:number ] )
 * Before sending response, server sends location entries, ending with "done".
 * location-entry: ( range-start:number range-end:number [ abs-path:string ] ) | done
 * response: ( )
</pre> *
 *
 * @author a.navrotskiy
 */
class GetLocationSegmentsCmd : BaseCmd<GetLocationSegmentsCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val writer: SvnServerWriter = context.writer
        val endRev: Int = getRevision(args.endRev, 0)
        val pegRev: Int = getRevisionOrLatest(args.pegRev, context)
        val startRev: Int = getRevision(args.startRev, pegRev)
        if ((endRev > startRev) || (startRev > pegRev)) {
            writer.word("done")
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Invalid revision range: peg: $pegRev, start: $startRev, end $endRev"))
        }
        var fullPath: String = context.getRepositoryPath(args.path)
        val lastChange: Int? = context.branch.getLastChange(fullPath, pegRev)
        if (lastChange == null) {
            writer.word("done")
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "File not found: $fullPath@$pegRev"))
        }
        var maxRev: Int = pegRev
        while (maxRev >= endRev) {
            var minRev: Int = maxRev
            while (true) {
                val change: Int? = context.branch.getLastChange(fullPath, minRev - 1)
                if (change != null) {
                    minRev = change
                } else {
                    break
                }
            }
            if (minRev <= startRev) {
                writer
                    .listBegin()
                    .number(max(minRev, endRev).toLong())
                    .number(min(maxRev, startRev).toLong())
                    .listBegin().string(fullPath).listEnd()
                    .listEnd()
            }
            val copyFrom: VcsCopyFrom? = context.branch.getRevisionInfo(minRev).getCopyFrom(fullPath)
            if (copyFrom == null || !context.canRead(copyFrom.path)) break
            maxRev = copyFrom.revision
            fullPath = copyFrom.path
        }
        writer
            .word("done")
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .listEnd()
            .listEnd()
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: Params) {
        context.checkRead(context.getRepositoryPath(args.path))
    }

    class Params constructor(val path: String, val pegRev: IntArray, val startRev: IntArray, val endRev: IntArray)
}
