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
import svnserver.repository.git.GitLogEntry
import svnserver.repository.git.GitRevision
import svnserver.server.SessionContext
import java.io.IOException
import java.util.*
import kotlin.math.max

/**
 * <pre>
 * log
 * params:   ( ( target-path:string ... ) [ start-rev:number ]
 * [ end-rev:number ] changed-paths:bool strict-node:bool
 * ? limit:number
 * ? include-merged-revisions:bool
 * all-revprops | revprops ( revprop:string ... ) )
 * Before sending response, server sends log entries, ending with "done".
 * If a client does not want to specify a limit, it should send 0 as the
 * limit parameter.  rev-props excludes author, date, and log; they are
 * sent separately for backwards-compatibility.
 * log-entry: ( ( change:changed-path-entry ... ) rev:number
 * [ author:string ] [ date:string ] [ message:string ]
 * ? has-children:bool invalid-revnum:bool
 * revprop-count:number rev-props:proplist
 * ? subtractive-merge:bool )
 * | done
 * changed-path-entry: ( path:string A|D|R|M
 * ? ( ? copy-path:string copy-rev:number )
 * ? ( ? node-kind:string ? text-mods:bool prop-mods:bool ) )
 * response: ( )
</pre> *
 *
 * @author a.navrotskiy
 */
class LogCmd : BaseCmd<LogCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val writer: SvnServerWriter = context.writer
        val head: Int = context.branch.latestRevision.id
        val endRev: Int = getRevision(args.endRev, head)
        val startRev: Int = getRevision(args.startRev, 1)
        if (startRev > head || endRev > head) {
            writer.word("done")
            throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "No such revision " + max(startRev, endRev)))
        }
        val log: MutableList<GitRevision>
        if (startRev >= endRev) {
            log = getLog(context, args, startRev, endRev, args.limit)
        } else {
            val logReverse: List<GitRevision> = getLog(context, args, endRev, startRev, -1)
            val minIndex: Int = if (args.limit <= 0) 0 else max(0, logReverse.size - args.limit)
            log = ArrayList(logReverse.size - minIndex)
            for (i in logReverse.size - 1 downTo minIndex) {
                log.add(logReverse[i])
            }
        }
        for (revisionInfo: GitRevision in log) {
            writer
                .listBegin()
                .listBegin()
            if (args.changedPaths) {
                val changes: Map<String, GitLogEntry> = revisionInfo.changes
                writer.separator()
                for (entry: Map.Entry<String, GitLogEntry> in changes.entries) {
                    val logEntry: GitLogEntry = entry.value
                    val change: Char = logEntry.change
                    if (change.code == 0) continue
                    writer
                        .listBegin()
                        .string((entry.key)) // Path
                        .word(change)
                        .listBegin()
                    val copyFrom: VcsCopyFrom? = logEntry.copyFrom
                    if (copyFrom != null) {
                        writer.string(copyFrom.path)
                        writer.number(copyFrom.revision.toLong())
                    }
                    writer.listEnd()
                        .listBegin()
                        .string(logEntry.kind.toString())
                        .bool(logEntry.isContentModified) // text-mods
                        .bool(logEntry.isPropertyModified) // prop-mods
                        .listEnd()
                        .listEnd()
                        .separator()
                }
            }
            val revProps: Map<String, String> = revisionInfo.getProperties(false)
            writer.listEnd()
                .number(revisionInfo.id.toLong())
                .stringNullable(revisionInfo.author)
                .stringNullable(revisionInfo.dateString)
                .stringNullable(revisionInfo.log)
                .bool(false)
                .bool(false)
                .number(revProps.size.toLong())
                .writeMap(revProps)
                .listEnd()
                .separator()
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
        for (path: String in args.targetPath) context.checkRead(context.getRepositoryPath(path))
    }

    /**
     * TODO: This method is very similar to GetFileRevsCmd#walkFileHistory. Maybe they can be combined?
     */
    @Throws(SVNException::class)
    private fun getLog(context: SessionContext, args: Params, endRev: Int, startRev: Int, limit: Int): MutableList<GitRevision> {
        val targetPaths = ArrayList<VcsCopyFrom>()
        var revision = -1
        for (target in args.targetPath) {
            val fullTargetPath = context.getRepositoryPath(target)
            val lastChange = context.branch.getLastChange(fullTargetPath, endRev)
            if (lastChange != null && lastChange >= startRev) {
                targetPaths.add(VcsCopyFrom(lastChange, fullTargetPath))
                revision = max(revision, lastChange)
            }
        }
        val result = ArrayList<GitRevision>()
        var logLimit: Int = limit
        while (revision >= startRev) {
            val revisionInfo: GitRevision = context.branch.getRevisionInfo(revision)
            result.add(revisionInfo)
            if (--logLimit == 0) break
            var nextRevision: Int = -1
            val iter = targetPaths.listIterator()
            while (iter.hasNext()) {
                val entry: VcsCopyFrom = iter.next()
                if (revision == entry.revision) {
                    val lastChange: Int? = context.branch.getLastChange(entry.path, revision - 1)
                    if (lastChange != null && lastChange >= revision) {
                        throw IllegalStateException()
                    }
                    if (lastChange == null) {
                        if (args.strictNode) {
                            iter.remove()
                            continue
                        }
                        val copyFrom: VcsCopyFrom? = revisionInfo.getCopyFrom(entry.path)
                        if (copyFrom != null) {
                            iter.set(copyFrom)
                            nextRevision = max(nextRevision, copyFrom.revision)
                        } else {
                            iter.remove()
                        }
                    } else {
                        iter.set(VcsCopyFrom(lastChange, entry.path))
                        nextRevision = max(nextRevision, lastChange)
                    }
                }
            }
            revision = nextRevision
        }
        return result
    }

    class Params constructor(
        val targetPath: Array<String>,
        val startRev: IntArray,
        val endRev: IntArray,
        val changedPaths: Boolean,
        val strictNode: Boolean,
        val limit: Int,
        /**
         * TODO: issue #26.
         */
        val includeMergedRevisions: Boolean,  /*
         * Broken-minded SVN feature we're unlikely to support EVER.
         */
        val revpropsMode: String,
        val revprops: Array<String>?
    )
}
