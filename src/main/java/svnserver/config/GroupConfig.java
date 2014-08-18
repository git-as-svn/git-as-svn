package svnserver.config;

import org.jetbrains.annotations.NotNull;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class GroupConfig {

  @NotNull
  public static final GroupConfig[] emptyArray = {};

  @NotNull
  private String name;

  @NotNull
  private String[] users = {};

  @NotNull
  public String getName() {
    return name;
  }

  public void setName(@NotNull String name) {
    this.name = name.trim();
  }

  @NotNull
  public String[] getUsers() {
    return users;
  }

  public void setUsers(@NotNull String[] users) {
    this.users = users;
  }
}
