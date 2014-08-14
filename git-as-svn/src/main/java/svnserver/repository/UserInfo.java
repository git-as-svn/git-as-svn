package svnserver.repository;

import org.jetbrains.annotations.NotNull;

/**
 * Информация о пользователе.
 *
 * @author a.navrotskiy
 */
public interface UserInfo {
  @NotNull
  public String getName();

  @NotNull
  public String getMail();
}
