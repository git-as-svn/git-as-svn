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
import org.tmatesoft.svn.core.SVNException;
import svnserver.context.Shared;

import java.util.Collection;
import java.util.Map;

/**
 * User storage.
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public interface UserDB extends Shared {

  @NotNull
  Collection<Authenticator> authenticators();

  User check(@NotNull String userName, @NotNull String password) throws SVNException;

  @Nullable
  User lookupByUserName(@NotNull String userName) throws SVNException;

  @Nullable
  User lookupByExternal(@NotNull String external) throws SVNException;

  default void updateEnvironment(@NotNull Map<String, String> environment, @NotNull User userInfo) {
  }
}
