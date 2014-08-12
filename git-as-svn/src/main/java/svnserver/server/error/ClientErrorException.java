package svnserver.server.error;

/**
 * Ошибка при обработке запроса клиента.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ClientErrorException extends SvnServerException {
  private final int code;

  public ClientErrorException(int code, String message) {
    super(message);
    this.code = code;
  }

  public int getCode() {
    return code;
  }
}
