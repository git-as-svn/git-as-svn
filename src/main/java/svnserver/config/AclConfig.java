package svnserver.config;

import org.jetbrains.annotations.NotNull;
import svnserver.auth.ACL;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class AclConfig {

  @NotNull
  private GroupConfig[] groups = GroupConfig.emptyArray;

  @NotNull
  private AccessConfig[] access = new AccessConfig[]{new AccessConfig("/", new String[]{ACL.EveryoneMarker})};

  @NotNull
  public GroupConfig[] getGroups() {
    return groups;
  }

  public void setGroups(@NotNull GroupConfig[] groups) {
    this.groups = groups;
  }

  @NotNull
  public AccessConfig[] getAccess() {
    return access;
  }

  public void setAccess(@NotNull AccessConfig[] access) {
    this.access = access;
  }
}
