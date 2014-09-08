package svnserver.server;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.SvnConstants;
import svnserver.auth.ACL;
import svnserver.auth.Authenticator;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.config.Config;
import svnserver.parser.MessageParser;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerToken;
import svnserver.parser.SvnServerWriter;
import svnserver.parser.token.ListBeginToken;
import svnserver.parser.token.ListEndToken;
import svnserver.repository.VcsRepository;
import svnserver.server.command.*;
import svnserver.server.msg.AuthReq;
import svnserver.server.msg.ClientInfo;
import svnserver.server.step.Step;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Сервер для предоставления доступа к git-у через протокол subversion.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnServer extends Thread {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(SvnServer.class);
  @NotNull
  private final UserDB userDB;
  @NotNull
  private final Map<String, BaseCmd<?>> commands = new HashMap<>();
  @NotNull
  private final VcsRepository repository;
  @NotNull
  private final Config config;
  @NotNull
  private final ServerSocket serverSocket;
  @NotNull
  private final ExecutorService poolExecutor;
  @NotNull
  private final ACL acl;
  private volatile boolean stopped = false;

  public SvnServer(@NotNull Config config) throws IOException, SVNException {
    setDaemon(true);
    this.config = config;

    userDB = config.getUserDB().create();

    commands.put("commit", new CommitCmd());
    commands.put("diff", new DeltaCmd(DiffParams.class));
    commands.put("get-latest-rev", new GetLatestRevCmd());
    commands.put("get-dated-rev", new GetDatedRevCmd());
    commands.put("get-dir", new GetDirCmd());
    commands.put("get-file", new GetFileCmd());
    commands.put("get-iprops", new GetIPropsCmd());
    commands.put("log", new LogCmd());
    commands.put("reparent", new ReparentCmd());
    commands.put("check-path", new CheckPathCmd());
    commands.put("rev-prop", new RevPropCmd());
    commands.put("rev-proplist", new RevPropListCmd());
    commands.put("stat", new StatCmd());
    commands.put("status", new DeltaCmd(StatusParams.class));
    commands.put("switch", new DeltaCmd(SwitchParams.class));
    commands.put("update", new DeltaCmd(UpdateParams.class));

    repository = config.getRepository().create();
    acl = new ACL(config.getAcl());
    serverSocket = new ServerSocket(config.getPort(), 0, InetAddress.getByName(config.getHost()));

    poolExecutor = Executors.newCachedThreadPool();
    log.info("Server bind: {}", serverSocket.getLocalSocketAddress());
  }

  public int getPort() {
    return serverSocket.getLocalPort();
  }

  @Override
  public void run() {
    log.info("Server is ready on port: {}", serverSocket.getLocalPort());
    while (!stopped) {
      final Socket client;
      try {
        client = this.serverSocket.accept();
      } catch (IOException e) {
        if (stopped) {
          log.info("Server Stopped");
          break;
        }
        log.error("Error accepting client connection", e);
        continue;
      }
      poolExecutor.execute(() -> {
        log.info("New connection from: {}", client.getRemoteSocketAddress());
        try (Socket clientSocket = client) {
          serveClient(clientSocket);
        } catch (EOFException | SocketException ignore) {
          // client disconnect is not a error
        } catch (SVNException | IOException e) {
          log.info("Client error:", e);
        } finally {
          log.info("Connection from {} closed", client.getRemoteSocketAddress());
        }
      });
    }
  }

  public void serveClient(@NotNull Socket socket) throws IOException, SVNException {
    socket.setTcpNoDelay(true);
    final SvnServerWriter writer = new SvnServerWriter(new BufferedOutputStream(socket.getOutputStream()));
    final SvnServerParser parser = new SvnServerParser(socket.getInputStream());

    final ClientInfo clientInfo = exchangeCapabilities(parser, writer);
    final User user = authenticate(parser, writer);
    log.info("User: {}", user);

    final String basePath = getBasePath(clientInfo.getUrl());
    final SessionContext context = new SessionContext(parser, writer, this, basePath, clientInfo, user);
    repository.updateRevisions();
    sendAnnounce(writer, basePath);

    while (!stopped) {
      try {
        Step step = context.poll();
        if (step != null) {
          step.process(context);
          continue;
        }

        final SvnServerToken token = parser.readToken();
        if (token != ListBeginToken.instance) {
          throw new IOException("Unexpected token: " + token);
        }
        final String cmd = parser.readText();
        BaseCmd command = commands.get(cmd);
        if (command != null) {
          log.info("Receive command: {}", cmd);
          Object param = MessageParser.parse(command.getArguments(), parser);
          parser.readToken(ListEndToken.class);
          //noinspection unchecked
          command.process(context, param);
        } else {
          log.warn("Unsupported command: {}", cmd);
          BaseCmd.sendError(writer, SVNErrorMessage.create(SVNErrorCode.RA_SVN_UNKNOWN_CMD, "Unsupported command: " + cmd));
          parser.skipItems();
        }
      } catch (SVNException e) {
        log.error("Command execution error", e);
        BaseCmd.sendError(writer, e.getErrorMessage());
      }
    }
  }

  @NotNull
  public ACL getAcl() {
    return acl;
  }

  @NotNull
  public VcsRepository getRepository() {
    return repository;
  }

  @NotNull
  private String getBasePath(@NotNull String url) throws SVNException {
    if (!url.startsWith(SvnConstants.URL_PREFIX)) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.BAD_URL));
    }
    int index = url.indexOf('/', SvnConstants.URL_PREFIX.length());
    if (index < 0) {
      index = url.length();
    }
    return url.substring(0, index) + '/';
  }

  private ClientInfo exchangeCapabilities(SvnServerParser parser, SvnServerWriter writer) throws IOException, SVNException {
    // Анонсируем поддерживаемые функции.
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .number(2)
        .number(2)
        .listBegin()
        .listEnd()
        .listBegin()
        .word("edit-pipeline")  // This is required.
        .word("svndiff1")  // We support svndiff1
        .word("absent-entries")  // We support absent-dir and absent-dir editor commands
            //.word("commit-revprops") // We don't currently have _any_ revprop support
            //.word("mergeinfo")       // Nope, not yet
        .word("depth")
        .word("inherited-props")       // Need for .gitattributes and .gitignore
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

  @NotNull
  private User authenticate(@NotNull SvnServerParser parser, @NotNull SvnServerWriter writer) throws IOException, SVNException {
    // Отправляем запрос на авторизацию.
    final Collection<Authenticator> authenticators = userDB.authenticators();
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listBegin()
        .word(String.join(" ", authenticators.stream().map(Authenticator::getMethodName).toArray(String[]::new)))
        .listEnd()
        .string(config.getRealm().isEmpty() ? repository.getUuid() : config.getRealm())
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

      return user;
    }
  }

  private void sendAnnounce(SvnServerWriter writer, @NotNull String baseUrl) throws IOException {
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .string(repository.getUuid())
        .string(baseUrl)
        .listBegin()
            //.word("mergeinfo")
        .listEnd()
        .listEnd()
        .listEnd();
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

  public void shutdown() throws InterruptedException, IOException {
    shutdown(0);
  }

  public void shutdown(int millis) throws InterruptedException, IOException {
    log.info("Shutdown server");
    stopped = true;
    serverSocket.close();
    join(millis);
    log.info("Server shutdowned");
  }
}
