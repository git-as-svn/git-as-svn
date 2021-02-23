/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server

import org.slf4j.Logger
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCompression
import org.tmatesoft.svn.core.io.SVNCapability
import svnserver.Loggers
import svnserver.auth.AnonymousAuthenticator
import svnserver.auth.Authenticator
import svnserver.auth.User
import svnserver.auth.UserDB
import svnserver.config.Config
import svnserver.context.SharedContext
import svnserver.parser.MessageParser
import svnserver.parser.SvnServerParser
import svnserver.parser.SvnServerWriter
import svnserver.parser.token.ListBeginToken
import svnserver.repository.RepositoryInfo
import svnserver.repository.RepositoryMapping
import svnserver.repository.git.GitBranch
import svnserver.server.command.*
import svnserver.server.msg.AuthReq
import svnserver.server.msg.ClientInfo
import svnserver.server.step.Step
import java.io.EOFException
import java.io.IOException
import java.net.*
import java.nio.file.Path
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Сервер для предоставления доступа к git-у через протокол subversion.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class SvnServer constructor(basePath: Path, config: Config) : Thread("SvnServer") {
    private val commands: MutableMap<String, BaseCmd<*>> = HashMap()
    private val connections: MutableMap<Long, Socket> = ConcurrentHashMap()
    private val repositoryMapping: RepositoryMapping<*>
    private val config: Config
    private val serverSocket: ServerSocket
    private val stopped: AtomicBoolean = AtomicBoolean(false)
    private val lastSessionId: AtomicLong = AtomicLong()
    val sharedContext: SharedContext
    private val threadPoolExecutor: ThreadPoolExecutor
    val port: Int
        get() {
            return serverSocket.localPort
        }

    override fun run() {
        log.info("Ready for connections on {}", serverSocket.localSocketAddress)
        while (!stopped.get()) {
            val client: Socket
            try {
                client = serverSocket.accept()
            } catch (e: IOException) {
                if (stopped.get()) {
                    log.info("Server stopped")
                    break
                }
                log.error("Error accepting client connection", e)
                continue
            }
            val sessionId: Long = lastSessionId.incrementAndGet()
            connections[sessionId] = client
            val task = Runnable {
                try {
                    client.use { clientSocket ->
                        SvnServerWriter(clientSocket.getOutputStream()).use { writer ->
                            log.info("New connection from: {}", client.remoteSocketAddress)
                            serveClient(clientSocket, writer)
                        }
                    }
                } catch (ignore: EOFException) {
                    // client disconnect is not a error
                } catch (ignore: SocketException) {
                } catch (e: SVNException) {
                    log.warn("Exception:", e)
                } catch (e: IOException) {
                    log.warn("Exception:", e)
                } finally {
                    shutdownConnection(sessionId)
                }
            }
            try {
                threadPoolExecutor.execute(task)
            } catch (e: RejectedExecutionException) {
                shutdownConnection(sessionId)
            }
        }
    }

    @Throws(IOException::class, SVNException::class)
    private fun serveClient(socket: Socket, writer: SvnServerWriter) {
        socket.tcpNoDelay = true
        val parser = SvnServerParser(socket.getInputStream())
        val clientInfo: ClientInfo = exchangeCapabilities(parser, writer)
        val repositoryInfo: RepositoryInfo = RepositoryMapping.findRepositoryInfo(repositoryMapping, clientInfo.url, writer) ?: return
        val context = SessionContext(parser, writer, this, repositoryInfo, clientInfo)
        context.authenticate(true)
        val branch: GitBranch = context.branch
        branch.updateRevisions()
        sendAnnounce(writer, repositoryInfo)
        while (!isInterrupted) {
            try {
                val step: Step? = context.poll()
                if (step != null) {
                    step.process(context)
                    continue
                }
                parser.readToken(ListBeginToken::class.java)
                val cmd: String = parser.readText()
                val command: BaseCmd<*>? = commands[cmd]
                if (command != null) {
                    log.debug("Receive command: {}", cmd)
                    command.process(context, parser)
                } else {
                    context.skipUnsupportedCommand(cmd)
                }
            } catch (e: SVNException) {
                if (WARNING_CODES.contains(e.errorMessage.errorCode)) {
                    log.warn("Command execution error: {}", e.message)
                } else {
                    log.error("Command execution error", e)
                }
                BaseCmd.sendError(writer, e.errorMessage)
            }
        }
    }

    private fun shutdownConnection(sessionId: Long) {
        val client: Socket? = connections.remove(sessionId)
        log.info("Connection from {} closed", client!!.remoteSocketAddress)
        try {
            client.close()
        } catch (e: IOException) {
            // It's ok
        }
    }

    @Throws(IOException::class, SVNException::class)
    private fun exchangeCapabilities(parser: SvnServerParser, writer: SvnServerWriter): ClientInfo {
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .number(2)
            .number(2)
            .listBegin()
            .listEnd()
            .listBegin()
        when (config.compressionLevel) {
            SVNDeltaCompression.LZ4 -> {
                writer
                    .word(svndiff2Capability)
                writer
                    .word(svndiff1Capability)
            }
            SVNDeltaCompression.Zlib -> writer
                .word(svndiff1Capability)
        }
        writer //.word(SVNCapability.COMMIT_REVPROPS.toString())
            .word(SVNCapability.DEPTH.toString()) //.word(SVNCapability.PARTIAL_REPLAY.toString()) TODO: issue #237
            .word("edit-pipeline")
            .word(SVNCapability.LOG_REVPROPS.toString()) //.word(SVNCapability.EPHEMERAL_PROPS.toString())
            .word(fileRevsReverseCapability)
            .word("absent-entries")
            .word(SVNCapability.INHERITED_PROPS.toString()) //.word("list") TODO: issue #162
        //.word(SVNCapability.ATOMIC_REVPROPS.toString())
        writer
            .listEnd()
            .listEnd()
            .listEnd()

        // Читаем информацию о клиенте.
        val clientInfo: ClientInfo = MessageParser.parse(ClientInfo::class.java, parser)
        log.info("Client: {}", clientInfo.raClient)
        if (clientInfo.protocolVersion != 2) throw SVNException(SVNErrorMessage.create(SVNErrorCode.VERSION_MISMATCH, "Unsupported protocol version: " + clientInfo.protocolVersion + " (expected: 2)"))
        return clientInfo
    }

    @Throws(IOException::class)
    private fun sendAnnounce(writer: SvnServerWriter, repositoryInfo: RepositoryInfo) {
        writer
            .listBegin()
            .word("success")
            .listBegin()
            .string(repositoryInfo.branch.uuid)
            .string(repositoryInfo.baseUrl.toString())
            .listBegin() //.word("mergeinfo")
            .listEnd()
            .listEnd()
            .listEnd()
    }

    @Throws(IOException::class, SVNException::class)
    fun authenticate(context: SessionContext, allowAnonymous: Boolean): User {
        // Отправляем запрос на авторизацию.
        val authenticators = ArrayList(sharedContext.sure(UserDB::class.java).authenticators())
        if (allowAnonymous) authenticators.add(0, AnonymousAuthenticator.get())
        context.writer
            .listBegin()
            .word("success")
            .listBegin()
            .listBegin()
            .word(authenticators.stream().map { obj: Authenticator -> obj.methodName }.toArray().joinToString(" "))
            .listEnd()
            .string(sharedContext.realm)
            .listEnd()
            .listEnd()
        while (true) {
            // Читаем выбранный вариант авторизации.
            val authReq: AuthReq = MessageParser.parse(AuthReq::class.java, context.parser)
            val authenticator: Optional<Authenticator> = authenticators.stream().filter { o: Authenticator -> (o.methodName == authReq.mech) }.findAny()
            if (!authenticator.isPresent) {
                sendError(context.writer, "unknown auth type: " + authReq.mech)
                continue
            }
            val user: User? = authenticator.get().authenticate(context, authReq.getToken())
            if (user == null) {
                sendError(context.writer, "incorrect credentials")
                continue
            }
            context.writer
                .listBegin()
                .word("success")
                .listBegin()
                .listEnd()
                .listEnd()
            log.info("User: {}", user)
            return user
        }
    }

    @Throws(Exception::class)
    fun shutdown(millis: Long) {
        startShutdown()
        if (!threadPoolExecutor.awaitTermination(millis, TimeUnit.MILLISECONDS)) {
            forceShutdown()
        }
        join(millis)
        sharedContext.close()
        log.info("Server shutdown complete")
    }

    @Throws(IOException::class)
    fun startShutdown() {
        if (stopped.compareAndSet(false, true)) {
            log.info("Shutdown server")
            serverSocket.close()
            threadPoolExecutor.shutdown()
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun forceShutdown() {
        for (socket: Socket in connections.values) {
            socket.close()
        }
        threadPoolExecutor.awaitTermination(FORCE_SHUTDOWN, TimeUnit.MILLISECONDS)
    }

    val compressionLevel: SVNDeltaCompression
        get() {
            return config.compressionLevel
        }

    companion object {
        const val svndiff1Capability: String = "svndiff1"
        const val svndiff2Capability: String = "accepts-svndiff2"

        /**
         * [is wrong.][SVNCapability.GET_FILE_REVS_REVERSED]
         */
        private const val fileRevsReverseCapability: String = "file-revs-reverse"
        private val log: Logger = Loggers.svn
        private val FORCE_SHUTDOWN: Long = TimeUnit.SECONDS.toMillis(5)
        private val WARNING_CODES: Set<SVNErrorCode> = hashSetOf(
            SVNErrorCode.CANCELLED,
            SVNErrorCode.ENTRY_NOT_FOUND,
            SVNErrorCode.FS_NOT_FOUND,
            SVNErrorCode.RA_NOT_AUTHORIZED,
            SVNErrorCode.REPOS_HOOK_FAILURE,
            SVNErrorCode.WC_NOT_UP_TO_DATE,
            SVNErrorCode.IO_WRITE_ERROR,
            SVNErrorCode.IO_PIPE_READ_ERROR,
            SVNErrorCode.RA_SVN_REPOS_NOT_FOUND,
            SVNErrorCode.AUTHZ_UNREADABLE,
            SVNErrorCode.AUTHZ_UNWRITABLE
        )
        private val threadNumber: AtomicInteger = AtomicInteger(1)

        @Throws(IOException::class)
        private fun sendError(writer: SvnServerWriter, msg: String) {
            writer
                .listBegin()
                .word("failure")
                .listBegin()
                .string(msg)
                .listEnd()
                .listEnd()
        }
    }

    init {
        isDaemon = true
        this.config = config
        val threadFactory = ThreadFactory { r: Runnable? ->
            val thread = Thread(r, String.format("SvnServer-thread-%s", threadNumber.incrementAndGet()))
            thread.isDaemon = true
            thread
        }
        threadPoolExecutor = ThreadPoolExecutor(
            0, Int.MAX_VALUE,
            60,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            threadFactory,
            AbortPolicy()
        )
        sharedContext = SharedContext.create(basePath, config.realm, config.cacheConfig.createCache(basePath), config.shared)
        sharedContext.add(UserDB::class.java, config.userDB.create(sharedContext))

        // Keep order as in https://svn.apache.org/repos/asf/subversion/trunk/subversion/libsvn_ra_svn/protocol
        commands["reparent"] = ReparentCmd()
        commands["get-latest-rev"] = GetLatestRevCmd()
        commands["get-dated-rev"] = GetDatedRevCmd()
        // change-rev-prop
        // change-rev-prop-2
        commands["rev-proplist"] = RevPropListCmd()
        commands["rev-prop"] = RevPropCmd()
        commands["commit"] = CommitCmd()
        commands["get-file"] = GetFileCmd()
        commands["get-dir"] = GetDirCmd()
        commands["check-path"] = CheckPathCmd()
        commands["stat"] = StatCmd()
        // get-mergeinfo
        commands["update"] = DeltaCmd(UpdateParams::class.java)
        commands["switch"] = DeltaCmd(SwitchParams::class.java)
        commands["status"] = DeltaCmd(StatusParams::class.java)
        commands["diff"] = DeltaCmd(DiffParams::class.java)
        commands["log"] = LogCmd()
        commands["get-locations"] = GetLocationsCmd()
        commands["get-location-segments"] = GetLocationSegmentsCmd()
        commands["get-file-revs"] = GetFileRevsCmd()
        commands["lock"] = LockCmd()
        commands["lock-many"] = LockManyCmd()
        commands["unlock"] = UnlockCmd()
        commands["unlock-many"] = UnlockManyCmd()
        commands["get-lock"] = GetLockCmd()
        commands["get-locks"] = GetLocksCmd()
        commands["replay"] = ReplayCmd()
        commands["replay-range"] = ReplayRangeCmd()
        // get-deleted-rev
        commands["get-iprops"] = GetIPropsCmd()
        // TODO: list (#162)
        repositoryMapping = config.repositoryMapping.create(sharedContext, config.parallelIndexing)
        sharedContext.add(RepositoryMapping::class.java, repositoryMapping)
        serverSocket = ServerSocket()
        serverSocket.reuseAddress = config.reuseAddress
        serverSocket.bind(InetSocketAddress(InetAddress.getByName(config.host), config.port))
        var success = false
        try {
            sharedContext.ready()
            success = true
        } finally {
            if (!success) sharedContext.close()
        }
    }
}
