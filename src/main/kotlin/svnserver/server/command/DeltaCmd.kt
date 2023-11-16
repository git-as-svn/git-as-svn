/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command

import org.slf4j.Logger
import org.tmatesoft.svn.core.*
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCompression
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow
import svnserver.Loggers
import svnserver.StringHelper
import svnserver.parser.SvnServerParser
import svnserver.parser.SvnServerWriter
import svnserver.parser.token.ListBeginToken
import svnserver.parser.token.ListEndToken
import svnserver.repository.Depth
import svnserver.repository.SvnForbiddenException
import svnserver.repository.VcsCopyFrom
import svnserver.repository.git.GitFile
import svnserver.server.SessionContext
import svnserver.server.step.CheckPermissionStep
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.OutputStream
import java.util.*

/**
 * Delta commands.
 * <pre>
 * To reduce round-trip delays, report commands do not return responses.
 * Any errors resulting from a report call will be returned to the client
 * by the command which invoked the report (following an abort-edit
 * call).  Errors resulting from an abort-report call are ignored.
 *
 * set-path:
 * params: ( path:string rev:number start-empty:bool
 * ? [ lock-token:string ] ? depth:word )
 *
 * delete-path:
 * params: ( path:string )
 *
 * link-path:
 * params: ( path:string url:string rev:number start-empty:bool
 * ? [ lock-token:string ] ? depth:word )
 *
 * finish-report:
 * params: ( )
 *
 * abort-report
 * params: ( )
</pre> *
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class DeltaCmd(override val arguments: Class<out DeltaParams>) : BaseCmd<DeltaParams>() {
    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: DeltaParams) {
        log.debug("Enter report mode")
        val pipeline = ReportPipeline(context, args)
        pipeline.reportCommand()
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: DeltaParams) {
        defaultPermissionCheck(context)
    }

    class DeleteParams(val path: String)
    class SetPathParams internal constructor(val path: String, val rev: Int, val startEmpty: Boolean, private val lockToken: Array<String>, depth: String) {
        val depth: Depth = Depth.parse(depth)
        override fun toString(): String {
            return ("SetPathParams{" + "path='" + path + '\'' + ", rev=" + rev + ", startEmpty=" + startEmpty + ", lockToken=" + lockToken.contentToString() + ", depth=" + depth + '}')
        }

    }

    private class FailureInfo(parser: SvnServerParser) {
        val errorCode: Int = parser.readNumber()
        val errorMessage: String = parser.readText()
        val errorFile: String = parser.readText()
        val errorLine: Int = parser.readNumber()

        @Throws(IOException::class)
        fun write(writer: SvnServerWriter) {
            writer.listBegin().number(errorCode.toLong()).string(errorMessage).string(errorFile).number(errorLine.toLong()).listEnd()
        }

        companion object {
            @Throws(IOException::class)
            fun read(parser: SvnServerParser): FailureInfo? {
                if (parser.readItem((ListBeginToken::class.java)) == null) {
                    return null
                }
                val result = FailureInfo(parser)
                parser.readToken(ListEndToken::class.java)
                return result
            }
        }

    }

    internal class ReportPipeline(private val context: SessionContext, private val params: DeltaParams) {

        private val deltaGenerator by lazy(mode = LazyThreadSafetyMode.NONE) { SVNDeltaGenerator() }
        private val commands: Map<String, BaseCmd<*>>
        private var forcedPaths = HashMap<String, MutableSet<String>>()
        private var deletedPaths = HashSet<String>()
        private val paths = HashMap<String, SetPathParams>()

        private val pathStack = ArrayDeque<HeaderEntry>()
        private var lastTokenId = 0

        @Throws(IOException::class, SVNException::class)
        private fun getWriter(context: SessionContext): SvnServerWriter {
            for (entry in pathStack) {
                entry.write()
            }
            return context.writer
        }

        @Throws(IOException::class, SVNException::class)
        private fun abortReport(context: SessionContext) {
            val writer: SvnServerWriter = getWriter(context)
            writer.listBegin().word("success").listBegin().listEnd().listEnd()
        }

        private fun finishReport(context: SessionContext) {
            context.push(CheckPermissionStep({ sessionContext: SessionContext -> complete(sessionContext) }, null))
        }

        fun setPathReport(path: String, rev: Int, startEmpty: Boolean, depth: SVNDepth) {
            internalSetPathReport(SetPathParams(path, rev, startEmpty, emptyArray(), depth.name), path)
        }

        private fun internalSetPathReport(args: SetPathParams, path: String) {
            val wcPath: String = wcPath(path)
            paths[wcPath] = args
            forcePath(wcPath)
        }

        private fun wcPath(name: String): String {
            return joinPath(params.path, name)
        }

        private fun forcePath(wcPath: String) {
            var path: String = wcPath
            while (path.isNotEmpty()) {
                val parent: String = StringHelper.parentDir(path)
                val items = forcedPaths.computeIfAbsent(parent) { HashSet() }
                if (!items.add(path)) {
                    break
                }
                path = parent
            }
        }

        private fun joinPath(prefix: String, name: String): String {
            if (name.isEmpty()) return prefix
            return if (prefix.isEmpty()) name else ("$prefix/$name")
        }

        private fun setPathReport(context: SessionContext, args: SetPathParams) {
            context.push { reportCommand() }
            internalSetPathReport(args, args.path)
        }

        @Throws(IOException::class, SVNException::class)
        fun reportCommand() {
            val parser: SvnServerParser = context.parser
            parser.readToken(ListBeginToken::class.java)
            val cmd: String = parser.readText()
            log.debug("Report command: {}", cmd)
            try {
                val command: BaseCmd<*>? = commands[cmd]
                if (command == null) {
                    context.skipUnsupportedCommand(cmd)
                    return
                }
                command.process(context, parser)
            } finally {
                log.debug("Report command complete")
            }
        }

        private fun deletePath(context: SessionContext, args: DeleteParams) {
            context.push { reportCommand() }
            val wcPath: String = wcPath(args.path)
            forcePath(wcPath)
            deletedPaths.add(wcPath)
        }

        @Throws(IOException::class, SVNException::class)
        private fun complete(context: SessionContext) {
            val writer: SvnServerWriter = getWriter(context)
            sendDelta(context)
            writer.listBegin().word("close-edit").listBegin().listEnd().listEnd()
            val parser: SvnServerParser = context.parser
            parser.readToken(ListBeginToken::class.java)
            when (val clientStatus: String = parser.readText()) {
                "failure" -> {
                    parser.readToken(ListBeginToken::class.java)
                    val failures = ArrayList<FailureInfo>()
                    while (true) {
                        val failure = FailureInfo.read(parser) ?: break
                        if (failure.errorFile.isEmpty()) {
                            log.warn("Received client error: {} {}", failure.errorCode, failure.errorMessage)
                        } else {
                            log.warn("Received client error [{}:{}]: {} {}", failure.errorFile, failure.errorLine, failure.errorCode, failure.errorMessage)
                        }
                        failures.add(failure)
                    }
                    parser.readToken(ListEndToken::class.java)
                    writer.listBegin().word("abort-edit").listBegin().listEnd().listEnd()
                    writer.listBegin().word("failure").listBegin()
                    for (failure: FailureInfo in failures) {
                        failure.write(writer)
                    }
                    writer.listEnd().listEnd()
                    writer.listBegin()
                }

                "success" -> {
                    parser.skipItems()
                    writer.listBegin().word("success").listBegin().listEnd().listEnd()
                }

                else -> {
                    log.error("Unexpected client status: {}", clientStatus)
                    throw EOFException("Unexpected client status")
                }
            }
        }

        @Throws(IOException::class, SVNException::class)
        fun sendDelta(context: SessionContext) {
            val path: String = params.path
            val targetRev: Int = params.getRev(context)
            val rootParams: SetPathParams = paths[wcPath("")] ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA))
            val writer: SvnServerWriter = getWriter(context)
            writer.listBegin().word("target-rev").listBegin().number(targetRev.toLong()).listEnd().listEnd()
            val tokenId: String = createTokenId()
            val rootRev: Int = rootParams.rev
            writer.listBegin().word("open-root").listBegin().listBegin().number(rootRev.toLong()).listEnd().string(tokenId).listEnd().listEnd()
            val fullPath: String = context.getRepositoryPath(path)
            val targetPath: SVNURL? = params.targetPath
            val newFile: GitFile? = if (targetPath == null) context.getFile(targetRev, fullPath) else context.getFile(targetRev, targetPath)
            val oldFile: GitFile? = getPrevFile(context, path, context.getFile(rootRev, fullPath))
            updateEntry(context, path, oldFile, newFile, tokenId, path.isEmpty(), rootParams.depth, params.depth)
            writer.listBegin().word("close-dir").listBegin().string(tokenId).listEnd().listEnd()
        }

        private fun createTokenId(): String {
            return "t" + ++lastTokenId
        }

        @Throws(IOException::class, SVNException::class)
        private fun updateEntry(
            context: SessionContext, wcPath: String, oldFile: GitFile?, newFile: GitFile?, parentTokenId: String, rootDir: Boolean, wcDepth: Depth, requestedDepth: Depth
        ) {
            if (oldFile != null) if (newFile == null || oldFile.kind != newFile.kind) removeEntry(context, wcPath, oldFile.lastChange.id, parentTokenId)
            if (newFile == null) return
            if (!context.canRead(newFile.fullPath)) {
                sendAbsent(context, newFile, parentTokenId)
                return
            }
            if (newFile.isDirectory) updateDir(context, wcPath, oldFile, newFile, parentTokenId, rootDir, wcDepth, requestedDepth) else {
                try {
                    updateFile(context, wcPath, oldFile, newFile, parentTokenId)
                } catch (ignored: SvnForbiddenException) {
                    sendAbsent(context, newFile, parentTokenId)
                }
            }
        }

        @Throws(IOException::class, SVNException::class)
        private fun updateDirEntries(
            context: SessionContext, wcPath: String, oldFile: GitFile?, newFile: GitFile, tokenId: String, wcDepth: Depth, requestedDepth: Depth
        ) {
            val dirAction = wcDepth.determineAction(requestedDepth, true)
            val fileAction = wcDepth.determineAction(requestedDepth, false)
            val forced = HashSet(forcedPaths.getOrDefault(wcPath, emptySet()))
            val oldEntries = handleDeletedEntries(newFile, oldFile, wcPath, context, tokenId, forced)

            for (newEntry in newFile.entries.values.map { it.get() }) {
                val entryPath: String = joinPath(wcPath, newEntry.fileName)
                val oldEntry: GitFile? = getPrevFile(context, entryPath, oldEntries[newEntry.fileName])
                val action: Depth.Action = if (newEntry.isDirectory) dirAction else fileAction
                if (!forced.remove(entryPath) && (newEntry == oldEntry) && (action == Depth.Action.Normal) && (requestedDepth === wcDepth)) // Same entry with same depth parameter.
                    continue
                if (action == Depth.Action.Skip) continue
                val entryDepth: Depth = getWcDepth(entryPath, wcDepth)
                updateEntry(context, entryPath, if (action == Depth.Action.Upgrade) null else oldEntry, newEntry, tokenId, false, entryDepth, requestedDepth.deepen())
            }
        }

        private fun handleDeletedEntries(newFile: GitFile, oldFile: GitFile?, wcPath: String, context: SessionContext, tokenId: String, forced: HashSet<String>): Map<String, GitFile> {
            val result = if (oldFile != null) {
                val map = HashMap<String, GitFile>()
                for (oldEntry in oldFile.entries.values.map { it.get() }) {
                    val entryPath: String = joinPath(wcPath, oldEntry.fileName)
                    if (newFile.entries.containsKey(oldEntry.fileName)) {
                        map[oldEntry.fileName] = oldEntry
                        continue
                    }
                    removeEntry(context, entryPath, oldEntry.lastChange.id, tokenId)
                    forced.remove(entryPath)
                }
                map
            } else {
                emptyMap()
            }
            for (entryPath in forced) {
                val entryName: String? = StringHelper.getChildPath(wcPath, entryPath)
                if ((entryName != null) && newFile.entries.contains(entryName)) {
                    continue
                }
                removeEntry(context, entryPath, newFile.lastChange.id, tokenId)
            }
            return result
        }

        @Throws(IOException::class, SVNException::class)
        private fun updateProps(context: SessionContext, type: String, tokenId: String, oldFile: GitFile?, newFile: GitFile) {
            val propsDiff = getPropertiesDiff(oldFile, newFile)
            if (oldFile == null) getWriter(context)
            for (entry in propsDiff.entries) {
                changeProp(getWriter(context), type, tokenId, entry.key, entry.value)
            }
        }

        @Throws(IOException::class, SVNException::class)
        private fun updateFile(context: SessionContext, wcPath: String, prevFile: GitFile?, newFile: GitFile, parentTokenId: String) {
            val tokenId: String = createTokenId()
            val md5: String = newFile.md5
            sendEntryHeader(context, wcPath, prevFile, newFile, "file", parentTokenId, tokenId) { writer: SvnServerWriter ->
                writer.listBegin().word("close-file").listBegin().string(tokenId).listBegin().string(md5).listEnd().listEnd().listEnd()
            }.use { header ->
                val oldFile: GitFile? = header.file
                if (oldFile == null || newFile.contentHash != oldFile.contentHash) {
                    val writer: SvnServerWriter = getWriter(context)
                    writer.listBegin().word("apply-textdelta").listBegin().string(tokenId).listBegin().listEnd().listEnd().listEnd()
                    if (params.textDeltas) {
                        (oldFile?.openStream() ?: SVNFileUtil.DUMMY_IN).use { source ->
                            newFile.openStream().use { target ->
                                val compression: SVNDeltaCompression = context.compression
                                val validateMd5: String = deltaGenerator.sendDelta(newFile.fileName, source, 0, target, object : ISVNDeltaConsumer {
                                    private var writeHeader = true
                                    override fun applyTextDelta(path: String, baseChecksum: String?) {}

                                    @Throws(SVNException::class)
                                    override fun textDeltaChunk(path: String, diffWindow: SVNDiffWindow): OutputStream? {
                                        try {
                                            ByteArrayOutputStream().use { stream ->
                                                diffWindow.writeTo(stream, writeHeader, compression)
                                                writeHeader = false
                                                writer.listBegin().word("textdelta-chunk").listBegin().string(tokenId).binary(stream.toByteArray()).listEnd().listEnd()
                                            }
                                            return null
                                        } catch (e: IOException) {
                                            throw SVNException(SVNErrorMessage.create(SVNErrorCode.IO_WRITE_ERROR), e)
                                        }
                                    }

                                    override fun textDeltaEnd(path: String) {}
                                }, true)
                                if (validateMd5 != md5) {
                                    throw IllegalStateException("MD5 checksum mismatch: some shit happends.")
                                }
                            }
                        }
                    }
                    writer.listBegin().word("textdelta-end").listBegin().string(tokenId).listEnd().listEnd()
                }
                updateProps(context, "file", tokenId, oldFile, newFile)
            }
        }

        private fun getWcDepth(wcPath: String, parentWcDepth: Depth): Depth {
            val params: SetPathParams = paths[wcPath] ?: return parentWcDepth.deepen()
            return params.depth
        }

        private fun getStartEmpty(wcPath: String): Boolean {
            val params: SetPathParams? = paths[wcPath]
            return params != null && params.startEmpty
        }

        @Throws(IOException::class, SVNException::class)
        private fun getPrevFile(context: SessionContext, wcPath: String, oldFile: GitFile?): GitFile? {
            if (deletedPaths.contains(wcPath)) return null
            val pathParams: SetPathParams = paths[wcPath] ?: return oldFile
            if (pathParams.rev == 0) return null
            return context.getFile(pathParams.rev, wcPath)
        }

        @Throws(IOException::class, SVNException::class)
        private fun sendAbsent(context: SessionContext, newFile: GitFile, parentTokenId: String) {
            getWriter(context).listBegin().word(if (newFile.isDirectory) "absent-dir" else "absent-file").listBegin().string(newFile.fileName).string(parentTokenId).listEnd().listEnd()
        }

        @Throws(IOException::class, SVNException::class)
        private fun updateDir(
            context: SessionContext, wcPath: String, prevFile: GitFile?, newFile: GitFile, parentTokenId: String, rootDir: Boolean, wcDepth: Depth, requestedDepth: Depth
        ) {
            val tokenId: String
            val header: HeaderEntry?
            var oldFile: GitFile?
            if (rootDir && wcPath.isEmpty()) {
                tokenId = parentTokenId
                oldFile = prevFile
                header = null
            } else {
                tokenId = createTokenId()
                header = sendEntryHeader(context, wcPath, prevFile, newFile, "dir", parentTokenId, tokenId) { writer: SvnServerWriter ->
                    writer.listBegin().word("close-dir").listBegin().string(tokenId).listEnd().listEnd()
                }
                oldFile = header.file
            }
            if (getStartEmpty(wcPath)) {
                oldFile = null
            }
            if (rootDir) {
                sendRevProps(getWriter(context), newFile, "dir", tokenId)
            }
            updateProps(context, "dir", tokenId, oldFile, newFile)
            updateDirEntries(context, wcPath, oldFile, newFile, tokenId, wcDepth, requestedDepth)
            header?.close()
        }

        @Throws(IOException::class, SVNException::class)
        private fun removeEntry(context: SessionContext, wcPath: String, rev: Int, parentTokenId: String) {
            if (deletedPaths.contains(wcPath)) {
                return
            }
            getWriter(context).listBegin().word("delete-entry").listBegin().string(wcPath).listBegin().number(rev.toLong()).listEnd().string(parentTokenId).listEnd().listEnd()
        }

        @Throws(IOException::class)
        private fun sendOpenEntry(writer: SvnServerWriter, command: String, fileName: String, parentTokenId: String, tokenId: String, revision: Int?) {
            writer.listBegin().word(command).listBegin().string(fileName).string(parentTokenId).string(tokenId).listBegin()
            if (revision != null) {
                writer.number(revision.toLong())
            }
            writer.listEnd().listEnd().listEnd()
        }

        @Throws(IOException::class, SVNException::class)
        private fun sendEntryHeader(context: SessionContext, wcPath: String, oldFile: GitFile?, newFile: GitFile, type: String, parentTokenId: String, tokenId: String, endWriter: HeaderWriter): HeaderEntry {
            return if (oldFile == null) {
                val copyFrom: VcsCopyFrom? = getCopyFrom(newFile)
                val entryFile: GitFile? = if (copyFrom != null) context.branch.getRevisionInfo(copyFrom.revision).getFile(copyFrom.path) else null
                val entry = HeaderEntry(context, entryFile, { writer: SvnServerWriter ->
                    sendNewEntry(writer, "add-$type", wcPath, parentTokenId, tokenId, copyFrom)
                    sendRevProps(writer, newFile, type, tokenId)
                }, endWriter, pathStack)
                getWriter(context)
                entry
            } else {
                HeaderEntry(context, oldFile, { writer: SvnServerWriter ->
                    sendOpenEntry(writer, "open-$type", wcPath, parentTokenId, tokenId, oldFile.lastChange.id)
                    sendRevProps(writer, newFile, type, tokenId)
                }, endWriter, pathStack)
            }
        }

        @Throws(IOException::class)
        private fun getCopyFrom(newFile: GitFile): VcsCopyFrom? {
            val copyFrom: VcsCopyFrom = params.sendCopyFrom.getCopyFrom(wcPath(""), newFile) ?: return null
            if (copyFrom.revision < params.lowRevision) return null
            return copyFrom
        }

        @Throws(IOException::class)
        private fun sendRevProps(writer: SvnServerWriter, newFile: GitFile, type: String, tokenId: String) {
            if (params.includeInternalProps) {
                for (prop in newFile.revProperties.entries) {
                    changeProp(writer, type, tokenId, prop.key, prop.value)
                }
            }
        }

        @Throws(IOException::class)
        private fun sendNewEntry(writer: SvnServerWriter, command: String, fileName: String, parentTokenId: String, tokenId: String, copyFrom: VcsCopyFrom?) {
            writer.listBegin().word(command).listBegin().string(fileName).string(parentTokenId).string(tokenId).listBegin()
            if (copyFrom != null) {
                writer.string(copyFrom.path)
                writer.number(copyFrom.revision.toLong())
            }
            writer.listEnd().listEnd().listEnd()
        }

        @Throws(IOException::class)
        private fun changeProp(writer: SvnServerWriter, type: String, tokenId: String, key: String, value: String?) {
            writer.listBegin().word("change-$type-prop").listBegin().string(tokenId).string(key).stringNullable(value).listEnd().listEnd()
        }

        private fun interface HeaderWriter {
            @Throws(IOException::class, SVNException::class)
            fun write(writer: SvnServerWriter)
        }

        private class HeaderEntry(private val context: SessionContext, val file: GitFile?, private val beginWriter: HeaderWriter, private val endWriter: HeaderWriter, private val pathStack: Deque<HeaderEntry>) : AutoCloseable {
            private var written: Boolean = false

            @Throws(IOException::class, SVNException::class)
            fun write() {
                if (!written) {
                    written = true
                    beginWriter.write(context.writer)
                }
            }

            @Throws(IOException::class, SVNException::class)
            override fun close() {
                if (written) {
                    endWriter.write(context.writer)
                }
                pathStack.removeLast()
            }

            init {
                pathStack.addLast(this)
            }
        }

        init {
            commands = hashMapOf(
                "delete-path" to LambdaCmd(DeleteParams::class.java) { context: SessionContext, args: DeleteParams -> deletePath(context, args) },
                "set-path" to LambdaCmd(SetPathParams::class.java) { context: SessionContext, args: SetPathParams -> this.setPathReport(context, args) },
                "abort-report" to LambdaCmd(NoParams::class.java) { context: SessionContext, _: NoParams -> abortReport(context) },
                "finish-report" to LambdaCmd(NoParams::class.java) { context: SessionContext, _: NoParams -> finishReport(context) },
            )
        }
    }

    companion object {
        private val log: Logger = Loggers.svn

        @Throws(IOException::class)
        fun getPropertiesDiff(oldFile: GitFile?, newFile: GitFile?): Map<String, String?> {
            val result = TreeMap<String, String?>()
            val oldProps = oldFile?.properties ?: emptyMap()
            val newProps = newFile?.properties ?: emptyMap()
            for (en in oldProps.entries) {
                val newProp = newProps[en.key]
                if (en.value != newProp) result[en.key] = newProp
            }
            for (en in newProps.entries) {
                val oldProp = oldProps[en.key]
                if (en.value != oldProp) result[en.key] = en.value
            }
            return result
        }
    }
}
