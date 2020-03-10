/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.auth.Authenticator;
import svnserver.auth.PlainAuthenticator;
import svnserver.auth.User;
import svnserver.auth.UserDB;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Testing UserDB implementation.
 *
 * @author Artem V. Navrotskiy
 */
final class TestUserDB implements UserDB {
  @NotNull
  private final User[] users;
  @NotNull
  private final StringBuilder report = new StringBuilder();

  TestUserDB(@NotNull User... users) {
    this.users = users;
  }

  @Override
  public User check(@NotNull String username, @NotNull String password) {
    log("check: " + username + ", " + password);
    if (password.equals(password(username))) {
      for (User user : users) {
        if (Objects.equals(user.getUsername(), username)) {
          return user;
        }
      }
    }
    return null;
  }

  private void log(@NotNull String message) {
    if (report.length() > 0) report.append('\n');
    report.append(message);
  }

  @NotNull
  public String password(@NotNull String username) {
    return "~~~" + username + "~~~";
  }

  @Nullable
  @Override
  public User lookupByUserName(@NotNull String username) {
    log("lookupByUserName: " + username);
    for (User user : users) {
      if (Objects.equals(user.getUsername(), username)) {
        return user;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public User lookupByExternal(@NotNull String external) {
    log("lookupByExternal: " + external);
    for (User user : users) {
      if (Objects.equals(user.getExternalId(), external)) {
        return user;
      }
    }
    return null;
  }

  @NotNull
  public String report() {
    return report.toString();
  }

  @NotNull
  @Override
  public Collection<Authenticator> authenticators() {
    return Collections.singletonList(new PlainAuthenticator(this));
  }
}
