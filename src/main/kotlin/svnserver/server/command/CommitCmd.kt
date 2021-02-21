/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server.command

import org.eclipse.jetty.util.MultiException
import org.slf4j.Logger
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.internal.delta.SVNDeltaReader
import svnserver.*
import svnserver.auth.*
import svnserver.parser.SvnServerParser
import svnserver.parser.SvnServerWriter
import svnserver.parser.token.ListBeginToken
import svnserver.repository.VcsConsumer
import svnserver.repository.git.*
import svnserver.repository.git.GitWriter.GitCommitBuilder
import svnserver.repository.locks.LockDesc
import svnserver.repository.locks.LockStorage
import svnserver.server.SessionContext
import svnserver.server.command.CommitCmd.Params
import svnserver.server.step.CheckPermissionStep
import java.io.Closeable
import java.io.IOException
import java.util.*

/**
 * Commit client changes.
 * <pre>
 * commit
 * params:   ( logmsg:string ? ( ( lock-path:string lock-token:string ) ... )
 * keep-locks:bool ? rev-props:proplist )
 * response: ( )
 * Upon receiving response, client switches to editor command set.
 * Upon successful completion of edit, server sends auth-request.
 * After auth exchange completes, server sends commit-info.
 * If rev-props is present, logmsg is ignored.  Only the svn:log entry in
 * rev-props (if any) will be used.
 * commit-info: ( new-rev:number date:string author:string
 * ? ( post-commit-err:string ) )
 * NOTE: when revving this, make 'logmsg' optional, or delete that parameter
 * and have the log message specified in 'rev-props'.
</pre> *
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class CommitCmd : BaseCmd<Params>() {
    override val arguments: Class<out Params>
        get() {
            return Params::class.java
        }

    @Throws(IOException::class, SVNException::class)
    override fun processCommand(context: SessionContext, args: Params) {
        val writer: SvnServerWriter = context.writer
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .listEnd()
            .listEnd()
        log.debug("Enter editor mode")
        EditorPipeline(context, args).use { pipeline -> pipeline.editorCommand(context) }
    }

    @Throws(IOException::class, SVNException::class)
    override fun permissionCheck(context: SessionContext, args: Params) {
        context.checkWrite(context.getRepositoryPath(""))
    }

    class LockInfo constructor(val path: String, val lockToken: String)
    class Params constructor(val message: String, val locks: Array<LockInfo>, val keepLocks: Boolean)
    class OpenRootParams constructor(val rev: IntArray, val token: String)
    class OpenParams constructor(val name: String, val parentToken: String, val token: String, val rev: IntArray)
    class CopyParams constructor(copyFrom: String, val rev: Int) {
        val copyFrom = if (copyFrom.isEmpty()) null else SVNURL.parseURIEncoded(copyFrom)
    }

    class AddParams constructor(val name: String, val parentToken: String, val token: String, val copyParams: CopyParams)
    class DeleteParams constructor(val name: String, val rev: IntArray, val parentToken: String)
    class TokenParams constructor(val token: String)
    class ChangePropParams constructor(val token: String, val name: String, val value: Array<String>)
    class ChecksumParams constructor(val token: String, val checksum: Array<String>)
    class DeltaChunkParams constructor(val token: String, val chunk: ByteArray)
    private class FileUpdater(val deltaConsumer: GitDeltaConsumer) : Closeable {
        val reader: SVNDeltaReader = SVNDeltaReader()

        @Throws(IOException::class)
        override fun close() {
            deltaConsumer.close()
        }
    }

    private class EntryUpdater(// New parent entry (destination)
        val entry: GitEntry, // Old source entry (source)
        val source: GitFile?, val head: Boolean
    ) {
        val props: MutableMap<String, String> = if (source == null) HashMap() else HashMap(source.properties)
        val changes: MutableList<VcsConsumer<GitCommitBuilder>> = ArrayList()

        @Throws(IOException::class, SVNException::class)
        fun getEntry(name: String): GitFile {
            if (source == null) {
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Can't find node: $name"))
            }
            return source.getEntry(name) ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Can't find node: " + name + " in " + source.fullPath))
        }

    }

    private class EditorPipeline(context: SessionContext, params: Params) : Closeable {
        private val rootEntry: EntryUpdater
        private val commands: MutableMap<String, BaseCmd<*>>
        private val exitCommands: MutableMap<String, BaseCmd<*>>
        private val message: String = params.message
        private val paths: MutableMap<String, EntryUpdater>
        private val files: MutableMap<String, FileUpdater>
        private val locks: Map<String, String>
        private val writer = context.branch.createWriter(context.user)
        private val keepLocks = params.keepLocks
        private var aborted: Boolean = false

        @Throws(SVNException::class, IOException::class)
        private fun addDir(context: SessionContext, args: AddParams) {
            val parent: EntryUpdater = getParent(args.parentToken)
            val source: GitFile?
            context.checkWrite(StringHelper.joinPath(parent.entry.fullPath, args.name))
            if (args.copyParams.copyFrom != null) {
                log.debug("Copy dir: {} from {} (rev: {})", args.name, args.copyParams.copyFrom, args.copyParams.rev)
                source = context.getFile(args.copyParams.rev, args.copyParams.copyFrom)
                if (source == null) {
                    throw SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Directory not found: " + args.copyParams.copyFrom + "@" + args.copyParams.rev))
                }
            } else {
                log.debug("Add dir: {}", args.name)
                source = null
            }
            val updater = EntryUpdater(parent.entry, source, false)
            paths[args.token] = updater
            parent.changes.add(VcsConsumer { treeBuilder: GitCommitBuilder ->
                treeBuilder.addDir(StringHelper.baseName(args.name), source)
                updateDir(treeBuilder, updater)
                treeBuilder.checkDirProperties(updater.props)
                treeBuilder.closeDir()
            })
        }

        @Throws(SVNException::class, IOException::class)
        private fun addFile(context: SessionContext, args: AddParams) {
            val parent: EntryUpdater = getParent(args.parentToken)
            val deltaConsumer: GitDeltaConsumer
            val fullPath: String = StringHelper.joinPath(parent.entry.fullPath, args.name)
            context.checkWrite(fullPath)
            deltaConsumer = if (args.copyParams.copyFrom != null) {
                log.debug("Copy file: {} from {} (rev: {})", fullPath, args.copyParams.copyFrom, args.copyParams.rev)
                val file: GitFile = context.getFile(args.copyParams.rev, args.copyParams.copyFrom) ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Can't find path: " + args.copyParams.copyFrom + "@" + args.copyParams.rev))
                writer.modifyFile(parent.entry, args.name, file)
            } else {
                log.debug("Add file: {}", parent)
                writer.createFile(parent.entry, args.name)
            }
            files[args.token] = FileUpdater(deltaConsumer)
            parent.changes.add(VcsConsumer { treeBuilder: GitCommitBuilder -> treeBuilder.saveFile(StringHelper.baseName(args.name), deltaConsumer, false) })
        }

        @Throws(SVNException::class)
        private fun changeDirProp(args: ChangePropParams) {
            val dir: EntryUpdater = paths[args.token] ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Invalid path token: " + args.token))
            changeProp(dir.props, args)
        }

        @Throws(SVNException::class)
        private fun changeFileProp(args: ChangePropParams) {
            changeProp(getFile(args.token).deltaConsumer.properties, args)
        }

        @Throws(SVNException::class, IOException::class)
        private fun deleteEntry(context: SessionContext, args: DeleteParams) {
            val parent: EntryUpdater = getParent(args.parentToken)
            val rev: Int = if (args.rev.isNotEmpty()) args.rev[0] else -1
            context.checkWrite(StringHelper.joinPath(parent.entry.fullPath, args.name))
            log.debug("Delete entry: {} (rev: {})", args.name, rev)
            val entry: GitFile = parent.getEntry(StringHelper.baseName(args.name))
            if (parent.head && (rev >= 0) && (parent.source != null)) checkUpToDate(entry, rev)
            checkLock(entry)
            parent.changes.add(VcsConsumer { treeBuilder: GitCommitBuilder -> treeBuilder.delete(entry.fileName) })
        }

        @Throws(SVNException::class, IOException::class)
        private fun openRoot(context: SessionContext, args: OpenRootParams) {
            val fullPath: String = context.getRepositoryPath("")
            val rootPath: Array<String> = fullPath.split("/").toTypedArray()
            var lastUpdater: EntryUpdater = rootEntry
            for (i in 1 until rootPath.size) {
                val name: String = rootPath[i]
                val entry: GitFile = lastUpdater.getEntry(name)
                val updater = EntryUpdater(entry, entry, true)
                lastUpdater.changes.add(VcsConsumer { treeBuilder: GitCommitBuilder ->
                    treeBuilder.openDir(name)
                    updateDir(treeBuilder, updater)
                    treeBuilder.closeDir()
                })
                lastUpdater = updater
            }
            val rev: Int = if (args.rev.isNotEmpty()) args.rev[0] else -1
            if (rev >= 0) {
                if (lastUpdater.source == null) {
                    throw IllegalStateException()
                }
                checkUpToDate(lastUpdater.source!!, rev)
                val props: Map<String, String> = lastUpdater.props
                lastUpdater.changes.add(VcsConsumer { treeBuilder: GitCommitBuilder -> treeBuilder.checkDirProperties(props) })
            }
            paths[args.token] = lastUpdater
        }

        @Throws(SVNException::class, IOException::class)
        private fun openDir(context: SessionContext, args: OpenParams) {
            val parent: EntryUpdater = getParent(args.parentToken)
            val rev: Int = if (args.rev.isNotEmpty()) args.rev[0] else -1
            log.debug("Modify dir: {} (rev: {})", args.name, rev)
            val sourceDir: GitFile = parent.getEntry(StringHelper.baseName(args.name))
            context.checkRead(sourceDir.fullPath)
            val dir = EntryUpdater(sourceDir, sourceDir, parent.head)
            if ((rev >= 0) && (parent.head)) checkUpToDate(sourceDir, rev)
            paths[args.token] = dir
            parent.changes.add(VcsConsumer { treeBuilder: GitCommitBuilder ->
                treeBuilder.openDir(StringHelper.baseName(args.name))
                updateDir(treeBuilder, dir)
                if (rev >= 0) {
                    treeBuilder.checkDirProperties(dir.props)
                }
                treeBuilder.closeDir()
            })
        }

        @Throws(SVNException::class, IOException::class)
        private fun openFile(context: SessionContext, args: OpenParams) {
            val parent: EntryUpdater = getParent(args.parentToken)
            val rev: Int = if (args.rev.isNotEmpty()) args.rev[0] else -1
            context.checkWrite(StringHelper.joinPath(parent.entry.fullPath, args.name))
            log.debug("Modify file: {} (rev: {})", args.name, rev)
            val vcsFile: GitFile = parent.getEntry(StringHelper.baseName(args.name))
            val deltaConsumer: GitDeltaConsumer = writer.modifyFile(parent.entry, vcsFile.fileName, vcsFile)
            files[args.token] = FileUpdater(deltaConsumer)
            if (parent.head && (rev >= 0)) checkUpToDate(vcsFile, rev)
            checkLock(vcsFile)
            parent.changes.add(VcsConsumer { treeBuilder: GitCommitBuilder -> treeBuilder.saveFile(StringHelper.baseName(args.name), deltaConsumer, true) })
        }

        private fun closeDir(args: TokenParams) {
            paths.remove(args.token)
        }

        @Throws(SVNException::class)
        private fun closeFile(args: ChecksumParams) {
            val file: FileUpdater = files.remove(args.token) ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Invalid file token: " + args.token))
            if (args.checksum.isNotEmpty()) {
                file.deltaConsumer.validateChecksum(args.checksum[0])
            }
        }

        @Throws(SVNException::class)
        private fun deltaChunk(args: DeltaChunkParams) {
            getFile(args.token).reader.nextWindow(args.chunk, 0, args.chunk.size, "", getFile(args.token).deltaConsumer)
        }

        @Throws(SVNException::class)
        private fun deltaEnd(args: TokenParams) {
            getFile(args.token).deltaConsumer.textDeltaEnd(null)
        }

        @Throws(SVNException::class)
        private fun deltaApply(args: ChecksumParams) {
            getFile(args.token).deltaConsumer.applyTextDelta(null, if (args.checksum.isEmpty()) null else args.checksum[0])
        }

        @Throws(IOException::class, SVNException::class)
        private fun closeEdit(context: SessionContext) {
            if (context.user.isAnonymous) {
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Anonymous users cannot create commits"))
            }
            if (paths.isNotEmpty()) {
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, "Found not closed directory tokens: " + paths.keys))
            }
            if (files.isNotEmpty()) {
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, "Found not closed file tokens: " + files.keys))
            }
            val revision: GitRevision = context.branch.repository.wrapLockWrite { lockStorage: LockStorage ->
                val oldLocks: List<LockDesc> = getLocks(context.user, lockStorage, locks)
                for (pass in 0 until MAX_PASS_COUNT) {
                    val newRevision: GitRevision? = updateDir(writer.createCommitBuilder(lockStorage, locks), rootEntry).commit(context.user, message)
                    if (newRevision != null) {
                        lockStorage.refreshLocks(context.user, context.branch, keepLocks, oldLocks.toTypedArray())
                        return@wrapLockWrite newRevision
                    }
                }
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.CANCELLED, "Can't commit changes to upstream repository."))
            }
            context.push(CheckPermissionStep({ svnContext: SessionContext -> complete(svnContext, revision) }, null))
            val writer: SvnServerWriter = context.writer
            writer
                .listBegin()
                .word("success")
                .listBegin()
                .listEnd()
                .listEnd()
        }

        @Throws(IOException::class)
        private fun abortEdit(context: SessionContext) {
            val writer: SvnServerWriter = context.writer
            writer
                .listBegin()
                .word("success")
                .listBegin()
                .listEnd()
                .listEnd()
        }

        @Throws(SVNException::class)
        private fun getParent(parentToken: String): EntryUpdater {
            return paths[parentToken] ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Invalid path token: $parentToken"))
        }

        @Throws(IOException::class, SVNException::class)
        private fun updateDir(treeBuilder: GitCommitBuilder, updater: EntryUpdater): GitCommitBuilder {
            for (consumer: VcsConsumer<GitCommitBuilder> in updater.changes) {
                consumer.accept(treeBuilder)
            }
            return treeBuilder
        }

        private fun changeProp(props: MutableMap<String, String>, args: ChangePropParams) {
            if (args.value.isNotEmpty()) {
                props[args.name] = args.value[0]
            } else {
                props.remove(args.name)
            }
        }

        @Throws(SVNException::class)
        private fun getFile(token: String): FileUpdater {
            return files[token] ?: throw SVNException(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Invalid file token: $token"))
        }

        @Throws(SVNException::class)
        private fun checkUpToDate(vcsFile: GitFile, rev: Int) {
            if (vcsFile.lastChange.id > rev) {
                throw SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_UP_TO_DATE, "Working copy is not up-to-date: " + vcsFile.fullPath))
            }
            rootEntry.changes.add(VcsConsumer { treeBuilder: GitCommitBuilder -> treeBuilder.checkUpToDate(vcsFile.fullPath, rev) })
        }

        private fun checkLock(vcsFile: GitFile) {
            rootEntry.changes.add(VcsConsumer { treeBuilder: GitCommitBuilder -> treeBuilder.checkLock(vcsFile.fullPath) })
        }

        @Throws(IOException::class)
        private fun getLocks(user: User, lockManager: LockStorage, locks: Map<String, String>): List<LockDesc> {
            val result = ArrayList<LockDesc>()
            for (entry in locks.entries) {
                for (lock in lockManager.getLocks(user, writer.branch, entry.key, entry.value)) {
                    if ((lock.token == entry.value)) {
                        result.add(lock)
                    }
                }
            }
            return result
        }

        @Throws(IOException::class)
        private fun complete(context: SessionContext, revision: GitRevision) {
            val writer: SvnServerWriter = context.writer
            writer
                .listBegin()
                .number(revision.id.toLong()) // rev number
                .stringNullable(revision.dateString) // date
                .stringNullable(revision.author)
                .listBegin().listEnd()
                .listEnd()
        }

        @Throws(IOException::class, SVNException::class)
        fun editorCommand(context: SessionContext) {
            val parser: SvnServerParser = context.parser
            parser.readToken(ListBeginToken::class.java)
            val cmd: String = parser.readText()
            log.debug("Editor command: {}", cmd)
            var command: BaseCmd<*>? = exitCommands[cmd]
            if (command == null) {
                context.push { sessionContext: SessionContext -> editorCommand(sessionContext) }
                command = commands[cmd]
            }
            if (command == null) {
                context.skipUnsupportedCommand(cmd)
                return
            }
            if (aborted) {
                parser.skipItems()
                return
            }
            try {
                command.process(context, parser)
            } catch (e: SVNException) {
                aborted = true
                throw e
            } catch (e: Throwable) {
                log.warn("Exception during in cmd $cmd", e)
                aborted = true
                throw e
            }
        }

        @Throws(IOException::class)
        override fun close() {
            val multiException = MultiException()
            for (updater: FileUpdater in files.values) {
                try {
                    updater.close()
                } catch (e: IOException) {
                    multiException.add(e)
                }
            }
            try {
                writer.close()
            } catch (e: Exception) {
                multiException.add(e)
            }
            try {
                multiException.ifExceptionThrow()
            } catch (e: Exception) {
                throw IOException(e)
            }
        }

        companion object {
            private fun getLocks(context: SessionContext, locks: Array<LockInfo>): Map<String, String> {
                val result: MutableMap<String, String> = HashMap()
                for (lock: LockInfo in locks) {
                    result[context.getRepositoryPath(lock.path)] = lock.lockToken
                }
                return result
            }
        }

        init {
            val entry: GitFile = context.branch.latestRevision.getFile("") ?: throw IllegalStateException("Repository root entry not found.")
            rootEntry = EntryUpdater(entry, entry, true)
            paths = HashMap()
            files = HashMap()
            locks = getLocks(context, params.locks)
            commands = HashMap()
            commands["add-dir"] = LambdaCmd(AddParams::class.java) { sessionContext: SessionContext, args: AddParams -> addDir(sessionContext, args) }
            commands["add-file"] = LambdaCmd(AddParams::class.java) { sessionContext: SessionContext, args: AddParams -> addFile(sessionContext, args) }
            commands["change-dir-prop"] = LambdaCmd(ChangePropParams::class.java) { _: SessionContext, args: ChangePropParams -> changeDirProp(args) }
            commands["change-file-prop"] = LambdaCmd(ChangePropParams::class.java) { _: SessionContext, args: ChangePropParams -> changeFileProp(args) }
            commands["delete-entry"] = LambdaCmd(DeleteParams::class.java) { sessionContext: SessionContext, args: DeleteParams -> deleteEntry(sessionContext, args) }
            commands["open-root"] = LambdaCmd(OpenRootParams::class.java) { sessionContext: SessionContext, args: OpenRootParams -> openRoot(sessionContext, args) }
            commands["open-dir"] = LambdaCmd(OpenParams::class.java) { sessionContext: SessionContext, args: OpenParams -> openDir(sessionContext, args) }
            commands["open-file"] = LambdaCmd(OpenParams::class.java) { sessionContext: SessionContext, args: OpenParams -> openFile(sessionContext, args) }
            commands["close-dir"] = LambdaCmd(TokenParams::class.java) { _: SessionContext, args: TokenParams -> closeDir(args) }
            commands["close-file"] = LambdaCmd(ChecksumParams::class.java) { _: SessionContext, args: ChecksumParams -> closeFile(args) }
            commands["textdelta-chunk"] = LambdaCmd(DeltaChunkParams::class.java) { _: SessionContext, args: DeltaChunkParams -> deltaChunk(args) }
            commands["textdelta-end"] = LambdaCmd(TokenParams::class.java) { _: SessionContext, args: TokenParams -> deltaEnd(args) }
            commands["apply-textdelta"] = LambdaCmd(ChecksumParams::class.java) { _: SessionContext, args: ChecksumParams -> deltaApply(args) }
            exitCommands = HashMap()
            exitCommands["close-edit"] = LambdaCmd(NoParams::class.java) { sessionContext: SessionContext, _: NoParams -> closeEdit(sessionContext) }
            exitCommands["abort-edit"] = LambdaCmd(NoParams::class.java) { sessionContext: SessionContext, _: NoParams -> abortEdit(sessionContext) }
        }
    }

    companion object {
        private const val MAX_PASS_COUNT: Int = 10
        private val log: Logger = Loggers.svn
    }
}
