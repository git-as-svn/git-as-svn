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
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCompression
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow
import svnserver.parser.SvnServerWriter
import svnserver.repository.VcsCopyFrom
import svnserver.repository.git.GitBranch
import svnserver.repository.git.GitFile
import svnserver.repository.git.GitRepository
import svnserver.repository.git.GitRevision
import svnserver.server.SessionContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.*

/**
 * <pre>
 * get-file-revs
 * params:   ( path:string [ start-rev:number ] [ end-rev:number ]
 * ? include-merged-revisions:bool )
 * Before sending response, server sends file-rev entries, ending with "done".
 * file-rev: ( path:string rev:number rev-props:proplist
 * file-props:propdelta ? merged-revision:bool )
 * | done
 * After each file-rev, the file delta is sent as one or more strings,
 * terminated by the empty string.  If there is no delta, server just sends
 * the terminator.
 * response: ( )
</pre> *
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class GetFileRevsCmd : BaseCmd<GetFileRevsCmd.Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val writer: SvnServerWriter = context.writer
        try {
            var startRev: Int = getRevisionOrLatest(args.startRev, context)
            var endRev: Int = getRevisionOrLatest(args.endRev, context)
            val reverse: Boolean = startRev > endRev
            if (reverse) {
                val tmp: Int = startRev
                startRev = endRev
                endRev = tmp
            }
            val fullPath: String = context.getRepositoryPath(args.path)
            val branch: GitBranch = context.branch
            val rev: Int = branch.getLastChange(fullPath, endRev) ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "$fullPath not found in revision $endRev"))
            val head: GitFile = branch.getRevisionInfo(rev).getFile(fullPath) ?: throw IllegalStateException()
            val history: MutableList<GitFile> = ArrayList()
            walkFileHistory(context, head, startRev) { e: GitFile -> history.add(e) }
            if (reverse) history.reverse()
            val compression: SVNDeltaCompression = context.compression
            for (index in history.indices.reversed()) {
                val oldFile: GitFile? = if (index <= history.size - 2) history[index + 1] else null
                val newFile: GitFile = history[index]
                val propsDiff: Map<String, String?> = DeltaCmd.getPropertiesDiff(oldFile, newFile)
                writer
                    .listBegin()
                    .string(newFile.fullPath)
                    .number(newFile.revision.toLong())
                    .writeMap(newFile.lastChange.getProperties(true))
                    .writeMap(propsDiff, true)
                    .bool(false) // TODO: issue #26. merged-revision
                    .listEnd()
                (oldFile?.openStream() ?: SVNFileUtil.DUMMY_IN).use { prevStream ->
                    newFile.openStream().use { newStream ->
                        SVNDeltaGenerator().sendDelta(fullPath, prevStream, 0, newStream, object : ISVNDeltaConsumer {
                            private var writeHeader: Boolean = true
                            override fun applyTextDelta(path: String, baseChecksum: String?) {}

                            @Throws(SVNException::class)
                            override fun textDeltaChunk(path: String, diffWindow: SVNDiffWindow): OutputStream? {
                                try {
                                    ByteArrayOutputStream().use { stream ->
                                        diffWindow.writeTo(stream, writeHeader, compression)
                                        writeHeader = false
                                        writer.binary(stream.toByteArray())
                                    }
                                } catch (e: IOException) {
                                    throw SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR))
                                }
                                return null
                            }

                            @Throws(SVNException::class)
                            override fun textDeltaEnd(path: String) {
                                try {
                                    writer.binary(GitRepository.emptyBytes)
                                } catch (e: IOException) {
                                    throw SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR))
                                }
                            }
                        }, false)
                    }
                }
            }
        } finally {
            // Yes, this is ugly. But otherwise, client hangs waiting forever.
            writer
                .word("done")
        }
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .listEnd()
            .listEnd()
    }

    /**
     * TODO: This method is very similar to LogCmd#getLog. Maybe they can be combined?
     */
    @Throws(SVNException::class, IOException::class)
    private fun walkFileHistory(context: SessionContext, start: GitFile, stopRev: Int, walker: FileHistoryWalker) {
        var head: GitFile? = start
        while (true) {
            walker.handle((head)!!)
            var copyFrom: VcsCopyFrom? = head.copyFrom
            if (copyFrom == null) {
                val prevRev: Int? = context.branch.getLastChange(head.fullPath, head.revision - 1)
                if (prevRev != null) {
                    // Same path, earlier commit
                    copyFrom = VcsCopyFrom(prevRev, head.fullPath)
                }
            }

            // If null, it is the first revision where file was created
            if (copyFrom == null) break
            if (copyFrom.revision < stopRev || !context.canRead(copyFrom.path)) break
            val prevRevision: GitRevision = context.branch.getRevisionInfo(copyFrom.revision)
            val file: GitFile = prevRevision.getFile(copyFrom.path) ?: throw IllegalStateException()
            head = file
        }
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: Params) {
        context.checkRead(context.getRepositoryPath(args.path))
    }

    internal fun interface FileHistoryWalker {
        @Throws(SVNException::class, IOException::class)
        fun handle(file: GitFile)
    }

    class Params constructor(
        val path: String, val startRev: IntArray, val endRev: IntArray,
        /**
         * TODO: issue #26.
         */
        private val includeMergedRevisions: Boolean
    )
}
