/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.UserType;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple user db with clear-text passwords.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class LocalUserDB implements UserDB {

  @NotNull
  private final Map<String, UserWithPassword> users = new HashMap<>();
  @NotNull
  private final Collection<Authenticator> authenticators = Collections.singleton(new CramMD5Authenticator(users::get));

  public LocalUserDB() {
  }

  @Nullable
  public User add(@NotNull String username, @NotNull String password, @NotNull String realName, String email) {
    if (users.containsKey(username)) {
      return null;
    }

    final UserWithPassword userWithPassword = new UserWithPassword(User.create(username, realName, email, username, UserType.Local), password);
    users.put(userWithPassword.getUser().getUsername(), userWithPassword);
    return userWithPassword.getUser();
  }

  @NotNull
  @Override
  public Collection<Authenticator> authenticators() {
    return authenticators;
  }

  @Override
  public User check(@NotNull String username, @NotNull String password) {
    final UserWithPassword userWithPassword = users.get(username);
    if (userWithPassword == null)
      return null;

    if (!userWithPassword.getPassword().equals(password))
      return null;

    return userWithPassword.getUser();
  }

  @Nullable
  @Override
  public User lookupByUserName(@NotNull String username) {
    final UserWithPassword user = users.get(username);
    return user == null ? null : user.getUser();
  }

  @Nullable
  @Override
  public User lookupByExternal(@NotNull String external) {
    return lookupByUserName(external);
  }
}
