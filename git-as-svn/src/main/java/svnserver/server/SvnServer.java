package svnserver.server;

import org.jetbrains.annotations.NotNull;
import svnserver.StringHelper;
import svnserver.parser.MessageParser;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerToken;
import svnserver.parser.SvnServerWriter;
import svnserver.parser.token.ListBeginToken;
import svnserver.parser.token.ListEndToken;
import svnserver.server.command.*;
import svnserver.server.error.AuthException;
import svnserver.server.error.ClientErrorException;
import svnserver.server.error.SvnServerException;
import svnserver.server.msg.AuthInfoReq;
import svnserver.server.msg.AuthReq;
import svnserver.server.step.Step;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервер для предоставления доступа к git-у через протокол subversion.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnServer {
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
        } catch (IOException e) {
          e.printStackTrace();
        } catch (SvnServerException e) {
          e.printStackTrace();
        }
      }).start();
    }
  }

  public void serveClient(@NotNull Socket socket) throws IOException, SvnServerException {
    final SvnServerWriter writer = new SvnServerWriter(socket.getOutputStream());
    final SvnServerParser parser = new SvnServerParser(socket.getInputStream());
    final SessionContext context = new SessionContext(writer);
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
    final AuthInfoReq authInfoReq = MessageParser.parse(AuthInfoReq.class, parser);
    if (authInfoReq.getProtocolVersion() != 2) {
      throw new ClientErrorException("Unsupported protocol version: " + authInfoReq.getProtocolVersion() + " (expected: 2)");
    }
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

    // Читаем выбранный вариант авторизации.
    final AuthReq authReq = MessageParser.parse(AuthReq.class, parser);
    if (!authReq.getMech().equals("CRAM-MD5")) {
      throw new AuthException("Unsupported authentication mechanism: " + authReq.getMech());
    }

    // Выполняем авторизацию.
    String msgId = "blablabla";
    writer
        .listBegin()
        .word("step")
        .listBegin()
        .string(msgId)
        .listEnd()
        .listEnd();

    // Читаем логин и пароль.
    String authData = parser.readText();
    String authRequire = "bozaro " + hmac(msgId, "password");
    if (!authData.equals(authRequire)) {
      throw new AuthException("Incorrect password");
    }

    writer
        .listBegin()
        .word("success")
        .listBegin()
        .listEnd()
        .listEnd();

    writer
        .listBegin()
        .word("success")
        .listBegin()
        .string("4f0c5325-dd55-4330-b24c-0e9e40eb504b")
        .string("svn://localhost")
        .listBegin()
            //.word("mergeinfo")
        .listEnd()
        .listEnd()
        .listEnd();

    final Map<String, BaseCmd<?>> commands = new HashMap<>();
    commands.put("get-latest-rev", new GetLatestRevCmd());
    commands.put("get-file", new GetFileCmd());
    commands.put("log", new LogCmd());
    commands.put("reparent", new ReparentCmd());
    commands.put("check-path", new CheckPathCmd());
    commands.put("stat", new StatCmd());

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
            .number(210001)
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
