/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.jetbrains.annotations.NotNull;
import svnserver.auth.LocalUserDB;
import svnserver.auth.User;
import svnserver.auth.UserDB;
import svnserver.auth.UserWithPassword;
import svnserver.config.serializer.ConfigType;
import svnserver.context.SharedContext;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
@ConfigType("localUsers")
public final class LocalUserDBConfig implements UserDBConfig {

  @NotNull
  private UserEntry[] users = UserEntry.emptyArray;

  public LocalUserDBConfig() {
  }

  public LocalUserDBConfig(@NotNull UserEntry[] users) {
    this.users = users;
  }

  @NotNull
  @Override
  public UserDB create(@NotNull SharedContext context) {
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

    @SuppressWarnings("UnusedDeclaration")
    public UserEntry() {
    }

    public UserEntry(@NotNull String username, @NotNull String realName, @NotNull String email, @NotNull String password) {
      this.username = username;
      this.realName = realName;
      this.email = email;
      this.password = password;
    }
  }
}
