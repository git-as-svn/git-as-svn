/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.SVNCapability;
import svnserver.auth.AnonymousAuthenticator;
import svnserver.auth.Authenticator;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.config.Config;
import svnserver.context.SharedContext;
import svnserver.parser.MessageParser;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerWriter;
import svnserver.parser.token.ListBeginToken;
import svnserver.parser.token.ListEndToken;
import svnserver.repository.RepositoryInfo;
import svnserver.repository.RepositoryMapping;
import svnserver.repository.VcsAccess;
import svnserver.repository.git.GitBranch;
import svnserver.server.command.*;
import svnserver.server.msg.AuthReq;
import svnserver.server.msg.ClientInfo;
import svnserver.server.step.Step;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Сервер для предоставления доступа к git-у через протокол subversion.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class SvnServer extends Thread {
  /**
   * {@link SVNCapability#GET_FILE_REVS_REVERSED is wrong.
   */
  @NotNull
  private static final String fileRevsReverseCapability = "file-revs-reverse";

  @NotNull
  private static final Logger log = LoggerFactory.getLogger(SvnServer.class);
  private static final long FORCE_SHUTDOWN = TimeUnit.SECONDS.toMillis(5);
  @NotNull
  private static final Set<SVNErrorCode> WARNING_CODES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
      SVNErrorCode.CANCELLED,
      SVNErrorCode.ENTRY_NOT_FOUND,
      SVNErrorCode.FS_NOT_FOUND,
      SVNErrorCode.RA_NOT_AUTHORIZED,
      SVNErrorCode.REPOS_HOOK_FAILURE,
      SVNErrorCode.WC_NOT_UP_TO_DATE,
      SVNErrorCode.IO_WRITE_ERROR,
      SVNErrorCode.IO_PIPE_READ_ERROR,
      SVNErrorCode.RA_SVN_REPOS_NOT_FOUND
  )));
  @NotNull
  private static AtomicInteger threadNumber = new AtomicInteger(1);
  @NotNull
  private final Map<String, BaseCmd<?>> commands = new HashMap<>();
  @NotNull
  private final Map<Long, Socket> connections = new ConcurrentHashMap<>();
  @NotNull
  private final RepositoryMapping<?> repositoryMapping;
  @NotNull
  private final Config config;
  @NotNull
  private final ServerSocket serverSocket;
  @NotNull
  private final AtomicBoolean stopped = new AtomicBoolean(false);
  @NotNull
  private final AtomicLong lastSessionId = new AtomicLong();
  @NotNull
  private final SharedContext context;

  public SvnServer(@NotNull File basePath, @NotNull Config config) throws IOException, SVNException {
    super("SvnServer");
    setDaemon(true);
    this.config = config;

    final ThreadFactory threadFactory = r -> {
      final Thread thread = new Thread(r, String.format("SvnServer-thread-%s", threadNumber.incrementAndGet()));
      thread.setDaemon(true);
      return thread;
    };

    context = SharedContext.create(basePath, config.getCacheConfig().createCache(basePath), threadFactory, config.getShared());
    context.add(UserDB.class, config.getUserDB().create(context));

    // Keep order as in https://svn.apache.org/repos/asf/subversion/trunk/subversion/libsvn_ra_svn/protocol

    commands.put("reparent", new ReparentCmd());
    commands.put("get-latest-rev", new GetLatestRevCmd());
    commands.put("get-dated-rev", new GetDatedRevCmd());
    // change-rev-prop
    // change-rev-prop-2
    commands.put("rev-proplist", new RevPropListCmd());
    commands.put("rev-prop", new RevPropCmd());
    commands.put("commit", new CommitCmd());
    commands.put("get-file", new GetFileCmd());
    commands.put("get-dir", new GetDirCmd());
    commands.put("check-path", new CheckPathCmd());
    commands.put("stat", new StatCmd());
    // get-mergeinfo
    commands.put("update", new DeltaCmd(UpdateParams.class));
    commands.put("switch", new DeltaCmd(SwitchParams.class));
    commands.put("status", new DeltaCmd(StatusParams.class));
    commands.put("diff", new DeltaCmd(DiffParams.class));
    commands.put("log", new LogCmd());
    commands.put("get-locations", new GetLocationsCmd());
    commands.put("get-location-segments", new GetLocationSegmentsCmd());
    commands.put("get-file-revs", new GetFileRevsCmd());
    commands.put("lock", new LockCmd());
    commands.put("lock-many", new LockManyCmd());
    commands.put("unlock", new UnlockCmd());
    commands.put("unlock-many", new UnlockManyCmd());
    commands.put("get-lock", new GetLockCmd());
    commands.put("get-locks", new GetLocksCmd());
    commands.put("replay", new ReplayCmd());
    commands.put("replay-range", new ReplayRangeCmd());
    // get-deleted-rev
    commands.put("get-iprops", new GetIPropsCmd());
    // TODO: list (#162)

    repositoryMapping = config.getRepositoryMapping().create(context, config.canUseParallelIndexing());

    context.add(RepositoryMapping.class, repositoryMapping);

    serverSocket = new ServerSocket();
    serverSocket.setReuseAddress(config.getReuseAddress());
    serverSocket.bind(new InetSocketAddress(InetAddress.getByName(config.getHost()), config.getPort()));

    context.ready();
  }

  public int getPort() {
    return serverSocket.getLocalPort();
  }

  @NotNull
  public SharedContext getContext() {
    return context;
  }

  @Override
  public void run() {
    log.info("Ready for connections on {}", serverSocket.getLocalSocketAddress());
    while (!stopped.get()) {
      final Socket client;
      try {
        client = this.serverSocket.accept();
      } catch (IOException e) {
        if (stopped.get()) {
          log.info("Server Stopped");
          break;
        }
        log.error("Error accepting client connection", e);
        continue;
      }
      long sessionId = lastSessionId.incrementAndGet();
      context.getThreadPoolExecutor().execute(() -> {
        log.info("New connection from: {}", client.getRemoteSocketAddress());
        try (Socket clientSocket = client;
             SvnServerWriter writer = new SvnServerWriter(new BufferedOutputStream(clientSocket.getOutputStream()))) {
          connections.put(sessionId, client);
          serveClient(clientSocket, writer);
        } catch (EOFException | SocketException ignore) {
          // client disconnect is not a error
        } catch (SVNException | IOException e) {
          log.info("Client error:", e);
        } finally {
          connections.remove(sessionId);
          log.info("Connection from {} closed", client.getRemoteSocketAddress());
        }
      });
    }
  }

  private void serveClient(@NotNull Socket socket, @NotNull SvnServerWriter writer) throws IOException, SVNException {
    socket.setTcpNoDelay(true);
    final SvnServerParser parser = new SvnServerParser(socket.getInputStream());

    final ClientInfo clientInfo = exchangeCapabilities(parser, writer);

    final RepositoryInfo repositoryInfo = RepositoryMapping.findRepositoryInfo(repositoryMapping, clientInfo.getUrl(), writer);
    if (repositoryInfo == null)
      return;

    final SessionContext context = new SessionContext(parser, writer, this, repositoryInfo, clientInfo);
    context.authenticate(hasAnonymousAuthenticator(repositoryInfo));
    final GitBranch branch = context.getBranch();
    branch.updateRevisions();
    sendAnnounce(writer, repositoryInfo);

    while (!isInterrupted()) {
      try {
        Step step = context.poll();
        if (step != null) {
          step.process(context);
          continue;
        }

        parser.readToken(ListBeginToken.class);

        final String cmd = parser.readText();
        final BaseCmd<?> command = commands.get(cmd);
        if (command != null) {
          log.debug("Receive command: {}", cmd);
          processCommand(context, command, parser);
        } else {
          context.skipUnsupportedCommand(cmd);
        }
      } catch (SVNException e) {
        if (WARNING_CODES.contains(e.getErrorMessage().getErrorCode())) {
          log.warn("Command execution error: {}", e.getMessage());
        } else {
          log.error("Command execution error", e);
        }
        BaseCmd.sendError(writer, e.getErrorMessage());
      }
    }
  }

  private ClientInfo exchangeCapabilities(SvnServerParser parser, SvnServerWriter writer) throws IOException, SVNException {
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .number(2)
        .number(2)
        .listBegin()
        .listEnd()
        .listBegin();

    if (config.isCompressionEnabled())
      writer.word("svndiff1");         // We support svndiff1 (compression)

    writer
        //.word(SVNCapability.COMMIT_REVPROPS.toString())
        .word(SVNCapability.DEPTH.toString())
        //.word(SVNCapability.PARTIAL_REPLAY.toString()) TODO: issue #237
        //.word("accepts-svndiff2") TODO: issue #163
        .word("edit-pipeline")
        .word(SVNCapability.LOG_REVPROPS.toString())
        //.word(SVNCapability.EPHEMERAL_PROPS.toString())
        .word(fileRevsReverseCapability)
        .word("absent-entries")
        .word(SVNCapability.INHERITED_PROPS.toString())
    //.word("list") TODO: issue #162
    //.word(SVNCapability.ATOMIC_REVPROPS.toString())
    ;

    writer
        .listEnd()
        .listEnd()
        .listEnd();

    // Читаем информацию о клиенте.
    final ClientInfo clientInfo = MessageParser.parse(ClientInfo.class, parser);
    if (clientInfo.getProtocolVersion() != 2) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.VERSION_MISMATCH, "Unsupported protocol version: " + clientInfo.getProtocolVersion() + " (expected: 2)"));
    }
    return clientInfo;
  }

  private boolean hasAnonymousAuthenticator(RepositoryInfo repositoryInfo) throws IOException {
    try {
      repositoryInfo.getBranch().getRepository().getContext().sure(VcsAccess.class).checkRead(User.getAnonymous(), null);
      return true;
    } catch (SVNException e) {
      return false;
    }
  }

  private void sendAnnounce(@NotNull SvnServerWriter writer, @NotNull RepositoryInfo repositoryInfo) throws IOException {
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .string(repositoryInfo.getBranch().getUuid())
        .string(repositoryInfo.getBaseUrl().toString())
        .listBegin()
        //.word("mergeinfo")
        .listEnd()
        .listEnd()
        .listEnd();
  }

  private static <T> void processCommand(@NotNull SessionContext context, @NotNull BaseCmd<T> cmd, @NotNull SvnServerParser parser) throws IOException, SVNException {
    final T param = MessageParser.parse(cmd.getArguments(), parser);
    parser.readToken(ListEndToken.class);
    cmd.process(context, param);
  }

  @NotNull
  public User authenticate(@NotNull SvnServerParser parser, @NotNull SvnServerWriter writer, @NotNull RepositoryInfo repositoryInfo, boolean allowAnonymous) throws IOException, SVNException {
    // Отправляем запрос на авторизацию.
    final List<Authenticator> authenticators = new ArrayList<>(context.sure(UserDB.class).authenticators());
    if (allowAnonymous) {
      authenticators.add(0, AnonymousAuthenticator.get());
    }
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listBegin()
        .word(String.join(" ", authenticators.stream().map(Authenticator::getMethodName).toArray(String[]::new)))
        .listEnd()
        .string(config.getRealm().isEmpty() ? repositoryInfo.getBranch().getUuid() : config.getRealm())
        .listEnd()
        .listEnd();

    while (true) {
      // Читаем выбранный вариант авторизации.
      final AuthReq authReq = MessageParser.parse(AuthReq.class, parser);
      final Optional<Authenticator> authenticator = authenticators.stream().filter(o -> o.getMethodName().equals(authReq.getMech())).findAny();
      if (!authenticator.isPresent()) {
        sendError(writer, "unknown auth type: " + authReq.getMech());
        continue;
      }

      final User user = authenticator.get().authenticate(parser, writer, authReq.getToken());
      if (user == null) {
        sendError(writer, "incorrect credentials");
        continue;
      }

      writer
          .listBegin()
          .word("success")
          .listBegin()
          .listEnd()
          .listEnd();

      log.info("User: {}", user);
      return user;
    }
  }

  private static void sendError(SvnServerWriter writer, String msg) throws IOException {
    writer
        .listBegin()
        .word("failure")
        .listBegin()
        .string(msg)
        .listEnd()
        .listEnd();
  }

  public void shutdown(long millis) throws Exception {
    startShutdown();
    if (!context.getThreadPoolExecutor().awaitTermination(millis, TimeUnit.MILLISECONDS)) {
      forceShutdown();
    }
    join(millis);
    context.close();
    log.info("Server shutdowned");
  }

  public void startShutdown() throws IOException {
    if (stopped.compareAndSet(false, true)) {
      log.info("Shutdown server");
      serverSocket.close();
      context.getThreadPoolExecutor().shutdown();
    }
  }

  private void forceShutdown() throws IOException, InterruptedException {
    for (Socket socket : connections.values()) {
      socket.close();
    }
    context.getThreadPoolExecutor().awaitTermination(FORCE_SHUTDOWN, TimeUnit.MILLISECONDS);
  }

  boolean isCompressionEnabled() {
    return config.isCompressionEnabled();
  }
}
