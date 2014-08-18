package svnserver.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.config.AccessConfig;
import svnserver.config.AclConfig;
import svnserver.config.GroupConfig;

import java.util.*;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class ACL {
  @NotNull
  public static final String EveryoneMarker = "*";

  @NotNull
  private final Set<String> groups = new HashSet<>();
  @NotNull
  private final Map<String, Set<String>> user2groups = new HashMap<>();

  @NotNull
  private final Map<String, Set<AclEntry>> path2acl = new HashMap<>();

  public ACL(@NotNull AclConfig config) {
    for (final GroupConfig group : config.getGroups()) {
      final String name = group.getName();

      if (name.isEmpty())
        throw new IllegalArgumentException("Group with empty name is not a good idea");

      if (group.getUsers().length == 0)
        throw new IllegalArgumentException("Group is empty: " + name);

      if (!groups.add(name))
        throw new IllegalArgumentException("Duplicate group found: " + name);

      for (String user : group.getUsers())
        user2groups.computeIfAbsent(user, s -> new HashSet<>()).add(name);
    }

    for (AccessConfig access : config.getAccess()) {
      final String path = access.getPath();

      if (!path.startsWith("/"))
        throw new IllegalArgumentException("ACL must start with slash (/): " + path);

      if (path.endsWith("/") && path.length() > 1)
        throw new IllegalArgumentException("ACL must not end with slash (/): " + path);

      if (access.getAllowed().length == 0)
        throw new IllegalArgumentException("ACL is empty: " + path);

      if (path2acl.get(path) != null)
        throw new IllegalArgumentException("Duplicate ACL: " + path);

      for (String allowed : access.getAllowed())
        addAccess(path, allowed);
    }
  }

  private void addAccess(@NotNull String path, @NotNull String allowed) {
    final AclEntry entry;
    if (allowed.equals(EveryoneMarker))
      entry = new EveryoneAclEntry();
    else if (allowed.startsWith("@")) {
      final String group = allowed.substring(1, allowed.length());

      if (!groups.contains(group))
        throw new IllegalArgumentException("ACL entry " + path + " uses unknown group: " + group);

      entry = new GroupAclEntry(group);
    } else {
      entry = new UserAclEntry(allowed);
    }

    if (!path2acl.computeIfAbsent(path, s -> new HashSet<>()).add(entry))
      throw new IllegalArgumentException("Duplicate ACL entry " + path + ": " + allowed);
  }

  public boolean check(@NotNull User user, @NotNull String path) {
    String toCheck = path;

    while (!toCheck.isEmpty()) {
      if (doCheck(user, toCheck))
        return true;

      toCheck = toCheck.substring(0, toCheck.lastIndexOf('/'));
    }

    return doCheck(user, "/");
  }

  private boolean doCheck(@NotNull User user, @NotNull String path) {
    for (AclEntry entry : path2acl.getOrDefault(path, Collections.<AclEntry>emptySet()))
      if (entry.allows(user.getUserName()))
        return true;

    return false;
  }

  private interface AclEntry {
    boolean allows(@NotNull String user);
  }

  private class GroupAclEntry implements AclEntry {
    @NotNull
    private final String group;

    private GroupAclEntry(@NotNull String group) {
      this.group = group;
    }

    @Override
    public boolean allows(@NotNull String user) {
      return user2groups.getOrDefault(user, Collections.emptySet()).contains(group);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      return o instanceof GroupAclEntry && group.equals(((GroupAclEntry) o).group);
    }

    @Override
    public int hashCode() {
      return group.hashCode();
    }
  }

  private class UserAclEntry implements AclEntry {
    @NotNull
    private final String user;

    private UserAclEntry(@NotNull String user) {
      this.user = user;
    }

    @Override
    public boolean allows(@NotNull String user) {
      return user.equals(this.user);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      return o instanceof UserAclEntry && user.equals(((UserAclEntry) o).user);
    }

    @Override
    public int hashCode() {
      return user.hashCode();
    }
  }

  private class EveryoneAclEntry implements AclEntry {

    private EveryoneAclEntry() {
    }

    @Override
    public boolean allows(@NotNull String user) {
      return true;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      return o instanceof EveryoneAclEntry;
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }
}
