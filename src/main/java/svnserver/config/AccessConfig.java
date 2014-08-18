package svnserver.config;

import org.jetbrains.annotations.NotNull;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class AccessConfig {

  @NotNull
  private String path = "/";

  @NotNull
  private String[] allowed = {};

  public AccessConfig() {
  }

  public AccessConfig(@NotNull String path, @NotNull String[] allowed) {
    this.path = path;
    this.allowed = allowed;
  }

  @NotNull
  public String getPath() {
    return path;
  }

  public void setPath(@NotNull String path) {
    this.path = path.trim();
  }

  @NotNull
  public String[] getAllowed() {
    return allowed;
  }

  public void setAllowed(@NotNull String[] allowed) {
    this.allowed = allowed;
  }
}
