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
  private RepositoryConfig repository = new RepositoryConfig();

  public void setPort(int port) {
    this.port = port;
  }

  public int getPort() {
    return port;
  }

  public void setRepository(@NotNull RepositoryConfig repository) {
    this.repository = repository;
  }

  @NotNull
  public RepositoryConfig getRepository() {
    return repository;
  }
}
