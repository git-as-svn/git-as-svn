package svnserver.server;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import svnserver.SvnConstants;
import svnserver.auth.Authenticator;
import svnserver.auth.LocalUserDB;
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
import svnserver.repository.git.GitRepository;
import svnserver.server.command.*;
import svnserver.server.msg.AuthReq;
import svnserver.server.msg.ClientInfo;
import svnserver.server.step.Step;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Сервер для предоставления доступа к git-у через протокол subversion.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnServer {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(SvnServer.class);
  @NotNull
  private final UserDB userDB = new LocalUserDB();
  @NotNull
  private final Map<String, BaseCmd<?>> commands = new HashMap<>();
  @NotNull
  private final VcsRepository repository;
  @NotNull
  private final Config config;

  public SvnServer(@NotNull Config config) throws IOException, SVNException {
    this.config = config;

    commands.put("commit", new CommitCmd());
    commands.put("diff", new DiffCmd());
    commands.put("get-latest-rev", new GetLatestRevCmd());
    commands.put("get-dir", new GetDirCmd());
    commands.put("get-file", new GetFileCmd());
    commands.put("log", new LogCmd());
    commands.put("reparent", new ReparentCmd());
    commands.put("check-path", new CheckPathCmd());
    commands.put("rev-prop", new RevPropCmd());
    commands.put("rev-proplist", new RevPropListCmd());
    commands.put("stat", new StatCmd());
    commands.put("update", new UpdateCmd());

    repository = new GitRepository(config.getRepository());
  }

  public void start() throws IOException {
    final ServerSocket serverSocket = new ServerSocket(config.getPort());
    while (true) {
      final Socket socket = serverSocket.accept();
      new Thread(() -> {
        log.info("New connection from: {}", socket.getRemoteSocketAddress());
        try (Socket client = socket) {
          serveClient(client);
        } catch (EOFException ignore) {
          // client disconnect is not a error
        } catch (IOException e) {
          e.printStackTrace();
        } catch (SVNException e) {
          e.printStackTrace();
        } finally {
          log.info("Connection from {} closed", socket.getRemoteSocketAddress());
        }
      }).start();
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
    final SessionContext context = new SessionContext(parser, writer, repository, basePath, clientInfo, user);
    sendAnnounce(writer, basePath);

    while (true) {
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
        log.info("Receive command: {}", cmd);
        BaseCmd command = commands.get(cmd);
        if (command != null) {
          Object param = MessageParser.parse(command.getArguments(), parser);
          parser.readToken(ListEndToken.class);
          //noinspection unchecked
          command.process(context, param);
        } else {
          BaseCmd.sendError(writer, SVNErrorMessage.create(SVNErrorCode.RA_SVN_UNKNOWN_CMD, "Unsupported command: " + cmd));
          parser.skipItems();
        }
      } catch (SVNException e) {
        BaseCmd.sendError(writer, e.getErrorMessage());
      }
    }
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
            //.word("depth")           // Nope, not yet
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
        .string("Realm name")
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
}
