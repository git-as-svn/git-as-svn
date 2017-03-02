/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.combine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.Authenticator;
import svnserver.auth.PlainAuthenticator;
import svnserver.auth.User;
import svnserver.auth.UserDB;

import java.io.IOException;
import java.util.*;

/**
 * Complex authentication.
 * Can combine multiple authenticators.
 *
 * @author Artem V. Navrotskiy
 */
public class CombineUserDB implements UserDB {
  @NotNull
  private final Collection<Authenticator> authenticators = Collections.singleton(new PlainAuthenticator(this));
  @NotNull
  private List<UserDB> userDBs;

  public CombineUserDB(@NotNull List<UserDB> userDBs) {
    this.userDBs = new ArrayList<>(userDBs);
  }

  @Nullable
  @Override
  public User check(@NotNull String userName, @NotNull String password) throws SVNException, IOException {
    return firstAvailable(userDB -> userDB.check(userName, password));
  }

  @Nullable
  @Override
  public User lookupByUserName(@NotNull String userName) throws SVNException, IOException {
    return firstAvailable(userDB -> userDB.lookupByUserName(userName));
  }

  @Nullable
  @Override
  public User lookupByExternal(@NotNull String external) throws SVNException, IOException {
    return firstAvailable(userDB -> userDB.lookupByExternal(external));
  }

  private User firstAvailable(@NotNull FirstAvailableCallback callback) throws IOException, SVNException {
    for (UserDB userDB : userDBs) {
      User user = callback.exec(userDB);
      if (user != null) {
        return user;
      }
    }
    return null;
  }

  @FunctionalInterface
  private interface FirstAvailableCallback {
    @Nullable
    User exec(UserDB userDB) throws SVNException, IOException;
  }

  @NotNull
  @Override
  public Collection<Authenticator> authenticators() {
    return authenticators;
  }
}
