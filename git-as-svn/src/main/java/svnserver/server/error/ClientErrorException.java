package svnserver.server.error;

/**
 * Ошибка при обработке запроса клиента.
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class ClientErrorException extends SvnServerException {
  public ClientErrorException(String message) {
    super(message);
  }
}
