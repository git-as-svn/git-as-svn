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
import java.util.*

/**
 * <pre>
 * get-locations
 * params:   ( path:string peg-rev:number ( rev:number ... ) )
 * Before sending response, server sends location entries, ending with "done".
 * location-entry: ( rev:number abs-path:number ) | done
 * response: ( )
</pre> *
 *
 * @author a.navrotskiy
 */
class GetLocationsCmd : BaseCmd<GetLocationsCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val writer: SvnServerWriter = context.writer
        val sortedRevs: IntArray = args.revs.copyOf(args.revs.size)
        Arrays.sort(sortedRevs)
        var fullPath: String = context.getRepositoryPath(args.path)
        var lastChange: Int? = context.branch.getLastChange(fullPath, args.pegRev)
        if (lastChange == null) {
            writer.word("done")
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "File not found: " + fullPath + "@" + args.pegRev))
        }
        for (i in sortedRevs.indices.reversed()) {
            val revision: Int = sortedRevs[i]
            if (revision > args.pegRev) {
                writer.word("done")
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "File not found: " + fullPath + "@" + args.pegRev + " at revision " + revision))
            }
            while ((lastChange != null) && (revision < lastChange)) {
                val change: Int? = context.branch.getLastChange(fullPath, lastChange - 1)
                if (change != null) {
                    lastChange = change
                    continue
                }
                val copyFrom: VcsCopyFrom? = context.branch.getRevisionInfo(lastChange).getCopyFrom(fullPath)
                if (copyFrom == null || !context.canRead(copyFrom.path)) {
                    lastChange = null
                    break
                }
                lastChange = copyFrom.revision
                fullPath = copyFrom.path
            }
            if (lastChange == null) break
            if (revision >= lastChange) {
                writer
                    .listBegin()
                    .number(revision.toLong())
                    .string(fullPath)
                    .listEnd()
            }
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

    class Params(val path: String, val pegRev: Int, val revs: IntArray)
}
