package svnserver.server.error;

/**
 * Ошибка при обработке данных.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@SuppressWarnings("AbstractClassExtendsConcreteClass")
public abstract class SvnServerException extends Exception {
  protected SvnServerException() {
  }

  protected SvnServerException(String message) {
    super(message);
  }

  protected SvnServerException(String message, Throwable cause) {
    super(message, cause);
  }

  protected SvnServerException(Throwable cause) {
    super(cause);
  }
}
