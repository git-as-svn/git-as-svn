/**
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

import java.io.IOException;

/**
 * User lookup type.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public enum UserLookup {
  UserName {
    @Nullable
    @Override
    public User lookup(@NotNull UserLookupVisitor visitor, @NotNull String value) throws SVNException, IOException {
      return visitor.lookupByUserName(value);
    }
  },
  External {
    @Nullable
    @Override
    public User lookup(@NotNull UserLookupVisitor visitor, @NotNull String value) throws SVNException, IOException {
      return visitor.lookupByExternal(value);
    }
  };

  @Nullable
  public abstract User lookup(@NotNull UserLookupVisitor visitor, @NotNull String value) throws SVNException, IOException;
}
