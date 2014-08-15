package svnserver.config;

import org.jetbrains.annotations.NotNull;
import svnserver.auth.LocalUserDB;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.auth.UserWithPassword;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class LocalUserDBConfig implements UserDBConfig {

  @NotNull
  private UserEntry[] users = UserEntry.emptyArray;

  public LocalUserDBConfig() {
  }

  public LocalUserDBConfig(@NotNull UserEntry[] users) {
    this.users = users;
  }

  @NotNull
  public UserEntry[] getUsers() {
    return users;
  }

  public void setUsers(@NotNull UserEntry[] users) {
    this.users = users;
  }

  @NotNull
  @Override
  public UserDB create() {
    final LocalUserDB result = new LocalUserDB();
    for (UserEntry user : users)
      result.add(new UserWithPassword(new User(user.username, user.realName, user.email), user.password));
    return result;
  }

  public static class UserEntry {

    @NotNull
    private static final UserEntry[] emptyArray = {};

    @NotNull
    private String username = "";

    @NotNull
    private String realName = "";

    @NotNull
    private String email = "";

    @NotNull
    private String password = "";

    public UserEntry() {
    }

    public UserEntry(@NotNull String username, @NotNull String realName, @NotNull String email, @NotNull String password) {
      this.username = username;
      this.realName = realName;
      this.email = email;
      this.password = password;
    }

    @NotNull
    public String getUsername() {
      return username;
    }

    public void setUsername(@NotNull String username) {
      this.username = username;
    }

    @NotNull
    public String getRealName() {
      return realName;
    }

    public void setRealName(@NotNull String realName) {
      this.realName = realName;
    }

    @NotNull
    public String getEmail() {
      return email;
    }

    public void setEmail(@NotNull String email) {
      this.email = email;
    }

    @NotNull
    public String getPassword() {
      return password;
    }

    public void setPassword(@NotNull String password) {
      this.password = password;
    }
  }
}
