package svnserver.config;

import org.jetbrains.annotations.NotNull;

/**
 * Top configuration object.
 *
 * @author a.navrotskiy
 */
public final class Config {
  private int port = 3690;

  @NotNull
  private String realm = "";

  @NotNull
  private RepositoryConfig repository = new RepositoryConfig();

  @NotNull
  private UserDBConfig userDB = new LocalUserDBConfig();

  public void setPort(int port) {
    this.port = port;
  }

  public int getPort() {
    return port;
  }

  @NotNull
  public String getRealm() {
    return realm;
  }

  public void setRealm(@NotNull String realm) {
    this.realm = realm;
  }
  public void setRepository(@NotNull RepositoryConfig repository) {
    this.repository = repository;
  }

  @NotNull
  public RepositoryConfig getRepository() {
    return repository;
  }

  public void setUserDB(@NotNull UserDBConfig userDB) {
    this.userDB = userDB;
  }

  @NotNull
  public UserDBConfig getUserDB() {
    return userDB;
  }
}
