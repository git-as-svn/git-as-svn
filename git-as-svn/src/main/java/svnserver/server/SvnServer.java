package svnserver.server;

import org.jetbrains.annotations.NotNull;
import svnserver.parser.SvnServerParser;
import svnserver.parser.SvnServerToken;
import svnserver.parser.SvnServerWriter;
import svnserver.parser.token.ListBeginToken;
import svnserver.parser.token.StringToken;
import svnserver.server.error.AuthException;
import svnserver.server.error.ClientErrorException;
import svnserver.server.error.SvnServerException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

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
    parser.readToken(ListBeginToken.class);
    int protocolVersion = parser.readNumber();
    String[] clientCaps = parser.readStringList();
    String url = parser.readText();
    parser.skipItems();
    if (protocolVersion != 2) {
      throw new ClientErrorException("Unsupported protocol version: " + protocolVersion + " (expected: 2)");
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
    parser.readToken(ListBeginToken.class);
    String authType = parser.readText();
    parser.skipItems();

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

    while (true) {
      final SvnServerToken token = parser.readToken();
      if (token == null) {
        break;
      }
      System.out.println(token);
    }
  }

  private static String hmac(@NotNull String sessionKey, @NotNull String password) {
    //noinspection OverlyBroadCatchBlock
    try {
      final SecretKeySpec keySpec = new SecretKeySpec(password.getBytes(), "HmacMD5");
      final Mac mac = Mac.getInstance("HmacMD5");
      mac.init(keySpec);
      return StringToken.toHex(mac.doFinal(sessionKey.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
