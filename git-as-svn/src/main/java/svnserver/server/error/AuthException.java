package svnserver.server.error;

/**
 * Ошибка при авторизации клиента.
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class AuthException extends SvnServerException {
  public AuthException(String message) {
    super(message);
  }
}
