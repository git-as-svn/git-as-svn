/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.jetbrains.annotations.NotNull;
import svnserver.auth.ACL;
import svnserver.repository.VcsAccess;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class AclConfig {

  @NotNull
  private GroupConfig[] groups = GroupConfig.emptyArray;

  @NotNull
  private AclAccessConfig[] access = new AclAccessConfig[]{new AclAccessConfig("/", new String[]{ACL.EveryoneMarker})};

  private boolean anonymousRead;

  public AclConfig() {
    this(false);
  }

  public AclConfig(boolean anonymousRead) {
    this.anonymousRead = anonymousRead;
  }

  @NotNull
  public GroupConfig[] getGroups() {
    return groups;
  }

  @NotNull
  public AclAccessConfig[] getAccess() {
    return access;
  }

  public boolean isAnonymousRead() {
    return anonymousRead;
  }

  public void setAnonymousRead(boolean anonymousRead) {
    this.anonymousRead = anonymousRead;
  }

  @NotNull
  public VcsAccess create() {
    return new ACL(this);
  }
}
