/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.auth.ACL;
import svnserver.config.serializer.ConfigType;
import svnserver.context.LocalContext;
import svnserver.repository.VcsAccess;

import java.io.IOException;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
@ConfigType("acl")
public final class AclConfig implements AccessConfig {

  @NotNull
  private GroupConfig[] groups = GroupConfig.emptyArray;

  @NotNull
  private AclAccessConfig[] access = new AclAccessConfig[]{new AclAccessConfig("/", new String[]{ACL.EveryoneMarker})};

  @NotNull
  public GroupConfig[] getGroups() {
    return groups;
  }

  public void setGroups(@NotNull GroupConfig[] groups) {
    this.groups = groups;
  }

  @NotNull
  public AclAccessConfig[] getAccess() {
    return access;
  }

  public void setAccess(@NotNull AclAccessConfig[] access) {
    this.access = access;
  }

  @NotNull
  @Override
  public VcsAccess create(@NotNull LocalContext context) throws IOException, SVNException {
    return new ACL(this);
  }
}
