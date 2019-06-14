/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth;

import org.eclipse.collections.api.block.function.primitive.BooleanFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import svnserver.StringHelper;
import svnserver.repository.RepositoryMapping;
import svnserver.repository.VcsAccess;

import java.util.*;

/**
 * This ACL reuses SVN's authz syntax as much as possible: http://svnbook.red-bean.com/nightly/en/svn.serverconfig.pathbasedauthz.html
 *
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class ACL implements VcsAccess {
  @NotNull
  static final String EveryoneMarker = "*";
  @NotNull
  static final String AnonymousMarker = "$anonymous";
  @NotNull
  static final String AuthenticatedMarker = "$authenticated";
  @NotNull
  private static final String GroupPrefix = "@";

  @NotNull
  private final Map<String, Set<String>> user2groups = new HashMap<>();
  @NotNull
  private final Set<String> anonymousGroups = new HashSet<>();
  @NotNull
  private final Set<String> authenticatedGroups = new HashSet<>();

  @NotNull
  private final NavigableMap<String, Map<ACLEntry, AccessMode>> path2acl = new TreeMap<>();

  public ACL(@NotNull Map<String, String[]> groups, @NotNull Map<String, Map<String, String>> access) {
    for (final Map.Entry<String, String[]> group : groups.entrySet()) {
      final String name = group.getKey();

      if (name.isEmpty())
        throw new IllegalArgumentException("Group with empty name is not a good idea");

      if (group.getValue().length == 0)
        throw new IllegalArgumentException("Group is empty: " + name);

      for (String member : group.getValue())
        if (member.equals(AnonymousMarker))
          anonymousGroups.add(name);
        else if (member.equals(AuthenticatedMarker))
          authenticatedGroups.add(name);
        else if (member.startsWith(GroupPrefix))
          throw new IllegalArgumentException("Groups of groups are not supported yet: " + name);
        else
          user2groups.computeIfAbsent(member, s -> new HashSet<>()).add(name);
    }

    for (Map.Entry<String, Map<String, String>> pathEntry : access.entrySet()) {
      final String path = pathEntry.getKey();

      if (!path.startsWith("/"))
        throw new IllegalArgumentException("ACL entry must start with slash (/): " + path);

      if (path.endsWith("/") && path.length() > 1)
        throw new IllegalArgumentException("ACL entry must not end with slash (/): " + path);

      if (pathEntry.getValue().isEmpty())
        throw new IllegalArgumentException("ACL entry is empty: " + path);

      for (Map.Entry<String, String> aclEntry : pathEntry.getValue().entrySet()) {
        final AccessMode accessMode = AccessMode.fromString(aclEntry.getValue());
        addAclEntry(path, aclEntry.getKey(), accessMode, groups.keySet());
      }
    }
  }

  private void addAclEntry(@NotNull String path, @NotNull String entryString, @NotNull AccessMode accessMode, @NotNull Set<String> allGroups) {
    final ACLEntry entry;
    if (entryString.equals(EveryoneMarker))
      entry = EveryoneACLEntry.instance;
    else if (entryString.equals(AnonymousMarker))
      entry = AnonymousACLEntry.instance;
    else if (entryString.equals(AuthenticatedMarker))
      entry = AuthenticatedACLEntry.instance;
    else if (entryString.startsWith(GroupPrefix)) {
      final String group = entryString.substring(1);

      if (!allGroups.contains(group))
        throw new IllegalArgumentException("ACL entry " + path + " uses unknown group: " + group);

      entry = new GroupACLEntry(group);
    } else
      entry = new UserACLEntry(entryString);

    if (path2acl.computeIfAbsent(StringHelper.normalizeDir(path), s -> new HashMap<>()).put(entry, accessMode) != null)
      throw new IllegalArgumentException("Duplicate ACL entry " + path + ": " + entryString);
  }

  @Override
  public boolean canRead(@NotNull User user, @NotNull String path) {
    return doCheck(user, path, AccessMode::allowsRead);
  }

  @Override
  public boolean canWrite(@NotNull User user, @NotNull String path) {
    return doCheck(user, path, AccessMode::allowsWrite);
  }

  private boolean doCheck(@NotNull User user, @NotNull String path, @NotNull BooleanFunction<AccessMode> checker) {
    String pathToCheck = path;

    while (true) {
      final Map.Entry<String, Map<ACLEntry, AccessMode>> pathEntry = RepositoryMapping.getMapped(path2acl, pathToCheck);

      if (pathEntry == null)
        break;

      final CheckResult checkResult = check(user, checker, pathEntry.getValue());
      if (checkResult == CheckResult.Deny)
        return false;
      else if (checkResult == CheckResult.Allow)
        return true;

      // We didn't find a matching entry, so need to go up in hierarchy
      final int prevSlash = pathEntry.getKey().lastIndexOf('/', pathEntry.getKey().length() - 2);
      if (prevSlash < 0)
        break;
      pathToCheck = pathEntry.getKey().substring(0, prevSlash);
    }

    return false;
  }

  @NotNull
  private ACL.CheckResult check(@NotNull User user, @NotNull BooleanFunction<AccessMode> checker, @NotNull Map<ACLEntry, AccessMode> entries) {
    CheckResult result = CheckResult.Unspecified;

    for (Map.Entry<ACLEntry, AccessMode> aclEntry : entries.entrySet()) {
      if (!aclEntry.getKey().matches(user))
        continue;

      if (checker.booleanValueOf(aclEntry.getValue()))
        // Explicit allow is stronger than explicit deny
        return CheckResult.Allow;

      result = CheckResult.Deny;
    }

    return result;
  }

  private enum CheckResult {
    Unspecified,
    Allow,
    Deny
  }

  private enum AccessMode {
    none,
    r,
    rw;

    @NotNull
    static AccessMode fromString(@Nullable String value) {
      if (value == null || value.isEmpty())
        return none;

      return valueOf(value);
    }

    boolean allowsRead() {
      return this != none;
    }

    boolean allowsWrite() {
      return this == rw;
    }
  }

  private interface ACLEntry {
    boolean matches(@NotNull User user);
  }

  private static final class EveryoneACLEntry implements ACLEntry {
    @NotNull
    private static final ACLEntry instance = new EveryoneACLEntry();

    private EveryoneACLEntry() {
    }

    @Override
    public boolean matches(@NotNull User user) {
      return true;
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      return o instanceof EveryoneACLEntry;
    }
  }

  private static final class UserACLEntry implements ACLEntry {
    @NotNull
    private final String user;

    private UserACLEntry(@NotNull String user) {
      this.user = user;
    }

    @Override
    public boolean matches(@NotNull User user) {
      return !user.isAnonymous() && user.getUserName().equals(this.user);
    }

    @Override
    public int hashCode() {
      return user.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      return o instanceof UserACLEntry && user.equals(((UserACLEntry) o).user);
    }
  }

  private static final class AnonymousACLEntry implements ACLEntry {
    @NotNull
    private static final ACLEntry instance = new AnonymousACLEntry();

    private AnonymousACLEntry() {
    }

    @Override
    public boolean matches(@NotNull User user) {
      return user.isAnonymous();
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      return o instanceof AnonymousACLEntry;
    }
  }

  private static final class AuthenticatedACLEntry implements ACLEntry {
    @NotNull
    private static final ACLEntry instance = new AuthenticatedACLEntry();

    private AuthenticatedACLEntry() {
    }

    @Override
    public boolean matches(@NotNull User user) {
      return !user.isAnonymous();
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      return o instanceof AuthenticatedACLEntry;
    }
  }

  private final class GroupACLEntry implements ACLEntry {
    @NotNull
    private final String group;

    private GroupACLEntry(@NotNull String group) {
      this.group = group;
    }

    @Override
    public boolean matches(@NotNull User user) {
      if (user.isAnonymous())
        return anonymousGroups.contains(group);

      if (authenticatedGroups.contains(group))
        return true;

      return user2groups.getOrDefault(user.getUserName(), Collections.emptySet()).contains(group);
    }

    @Override
    public int hashCode() {
      return group.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      return o instanceof GroupACLEntry && group.equals(((GroupACLEntry) o).group);
    }
  }
}
