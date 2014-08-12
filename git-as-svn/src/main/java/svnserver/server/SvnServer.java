package svnserver.server;

import org.jetbrains.annotations.NotNull;
import svnserver.StringHelper;
import svnserver.SvnConstants;
import svnserver.parser.MessageParser;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerToken;
import svnserver.parser.SvnServerWriter;
import svnserver.parser.token.ListBeginToken;
import svnserver.parser.token.ListEndToken;
import svnserver.repository.Repository;
import svnserver.repository.git.GitRepository;
import svnserver.server.command.*;
import svnserver.server.error.ClientErrorException;
import svnserver.server.error.SvnServerException;
import svnserver.server.msg.AuthReq;
import svnserver.server.msg.ClientInfo;
import svnserver.server.step.Step;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Сервер для предоставления доступа к git-у через протокол subversion.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnServer {

  @NotNull
  private final Map<String, String> users = new HashMap<>();
  @NotNull
  private final Map<String, BaseCmd<?>> commands = new HashMap<>();
  @NotNull
  private final Repository repository;

  public SvnServer() throws IOException {
    users.put("bozaro", "password");

    commands.put("get-latest-rev", new GetLatestRevCmd());
    commands.put("get-dir", new GetDirCmd());
    commands.put("get-file", new GetFileCmd());
    commands.put("log", new LogCmd());
    commands.put("reparent", new ReparentCmd());
    commands.put("check-path", new CheckPathCmd());
    commands.put("rev-prop", new RevPropCmd());
    commands.put("rev-proplist", new RevPropListCmd());
    commands.put("stat", new StatCmd());

    repository = new GitRepository();
  }

  public static void main(String[] args) throws IOException {
    new SvnServer().server(3690);
  }

  private void server(int port) throws IOException {
    final ServerSocket serverSocket = new ServerSocket(port);
    while (true) {
      final Socket socket = serverSocket.accept();
      new Thread(() -> {
        try (Socket client = socket) {
          serveClient(client);
        } catch (EOFException ignore) {
          // client disconnect is not a error
        } catch (IOException e) {
          e.printStackTrace();
        } catch (SvnServerException e) {
          e.printStackTrace();
        }
      }).start();
    }
  }

  public void serveClient(@NotNull Socket socket) throws IOException, SvnServerException {
    socket.setTcpNoDelay(true);
    final SvnServerWriter writer = new SvnServerWriter(new BufferedOutputStream(socket.getOutputStream()));
    final SvnServerParser parser = new SvnServerParser(socket.getInputStream());

    final ClientInfo clientInfo = exchangeCapabilities(parser, writer);
    final String username = authenticate(parser, writer);

    final String basePath = getBasePath(clientInfo.getUrl());
    final SessionContext context = new SessionContext(writer, repository, basePath, clientInfo.getUrl());
    sendAnnounce(writer, basePath);

    while (true) {
      Step step = context.poll();
      if (step != null) {
        step.process(context);
        continue;
      }

      SvnServerToken token = parser.readToken();
      if (token == null) {
        break;
      }
      if (token != ListBeginToken.instance) {
        throw new IOException("Unexpected token: " + token);
      }
      String cmd = parser.readText();
      BaseCmd command = commands.get(cmd);
      if (command != null) {
        Object param = MessageParser.parse(command.getArguments(), parser);
        parser.readToken(ListEndToken.class);
        //noinspection unchecked
        command.process(context, param);
      } else {
        writer
            .listBegin()
            .word("failure")
            .listBegin()
            .listBegin()
            .number(SvnConstants.ERROR_UNIMPLEMENTED)
            .string("Unsupported command: " + cmd)
            .string("...")
            .number(0)
            .listEnd()
            .listEnd()
            .listEnd();
        parser.skipItems();
      }
    }
  }

  @NotNull
  private String getBasePath(@NotNull String url) throws ClientErrorException {
    if (!url.startsWith(SvnConstants.URL_PREFIX)) {
      throw new ClientErrorException(0, "Invalid repository URL: " + url);
    }
    int index = url.indexOf('/', SvnConstants.URL_PREFIX.length());
    if (index < 0) {
      index = url.length();
    }
    return url.substring(0, index) + '/';
  }

  private ClientInfo exchangeCapabilities(SvnServerParser parser, SvnServerWriter writer) throws IOException, ClientErrorException {
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
      throw new ClientErrorException(0, "Unsupported protocol version: " + clientInfo.getProtocolVersion() + " (expected: 2)");
    }
    return clientInfo;
  }

  private String authenticate(SvnServerParser parser, SvnServerWriter writer) throws IOException {
    // Отправляем запрос на авторизацию.
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listBegin()
        .word("CRAM-MD5")
        .listEnd()
        .string("Realm name")
        .listEnd()
        .listEnd();

    while (true) {
      // Читаем выбранный вариант авторизации.
      final AuthReq authReq = MessageParser.parse(AuthReq.class, parser);
      if (!authReq.getMech().equals("CRAM-MD5")) {
        sendError(writer, "unknown auth type: " + authReq.getMech());
        continue;
      }

      // Выполняем авторизацию.
      String msgId = UUID.randomUUID().toString();
      writer
          .listBegin()
          .word("step")
          .listBegin()
          .string(msgId)
          .listEnd()
          .listEnd();

      // Читаем логин и пароль.
      final String[] authData = parser.readText().split(" ", 2);
      final String username = authData[0];

      final String password = users.get(username);
      if (password == null) {
        sendError(writer, "unknown user");
        continue;
      }

      final String authRequire = hmac(msgId, password);
      if (!authData[1].equals(authRequire)) {
        sendError(writer, "incorrect password");
        continue;
      }

      writer
          .listBegin()
          .word("success")
          .listBegin()
          .listEnd()
          .listEnd();

      return username;
    }
  }

  private void sendAnnounce(SvnServerWriter writer, @NotNull String baseUrl) throws IOException {
    writer
        .listBegin()
        .word("success")
        .listBegin()
        .string("4f0c5325-dd55-4330-b24c-0e9e40eb504b")
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

  private static String hmac(@NotNull String sessionKey, @NotNull String password) {
    //noinspection OverlyBroadCatchBlock
    try {
      final SecretKeySpec keySpec = new SecretKeySpec(password.getBytes(), "HmacMD5");
      final Mac mac = Mac.getInstance("HmacMD5");
      mac.init(keySpec);
      return StringHelper.toHex(mac.doFinal(sessionKey.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
