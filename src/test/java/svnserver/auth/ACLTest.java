/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.Test;
import svnserver.UserType;

import java.util.Collections;
import java.util.Map;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class ACLTest {

  @NotNull
  private static final User Bob = User.create("bob", "Bob", "bob@acme.com", null, UserType.Local);

  @NotNull
  private static final User Alice = User.create("alice", "Alice", "alice@acme.com", null, UserType.Local);

  @Test
  public void emptyDeny() {
    final ACL acl = new ACL(Collections.emptyMap(), Collections.emptyMap());

    Assert.assertFalse(acl.canRead(Bob, "/"));
    Assert.assertFalse(acl.canRead(User.getAnonymous(), "/"));
  }

  @Test
  public void groupOfGroup() {
    final Map<String, String[]> groups = ImmutableMap.<String, String[]>builder()
        .put("groupOfGroup", new String[]{"@group"})
        .put("group", new String[]{Bob.getUserName()})
        .build();
    final ACL acl = new ACL(groups, Collections.singletonMap("/", Collections.singletonMap("@groupOfGroup", "r")));

    Assert.assertTrue(acl.canRead(Bob, "/"));
    Assert.assertFalse(acl.canRead(User.getAnonymous(), "/"));
  }

  @Test
  public void groupOfGroupOfGroup() {
    final Map<String, String[]> groups = ImmutableMap.<String, String[]>builder()
        .put("groupOfGroupOfGroup", new String[]{"@groupOfGroup"})
        .put("groupOfGroup", new String[]{"@group"})
        .put("group", new String[]{Bob.getUserName()})
        .build();
    final ACL acl = new ACL(groups, Collections.singletonMap("/", Collections.singletonMap("@groupOfGroupOfGroup", "r")));

    Assert.assertTrue(acl.canRead(Bob, "/"));
    Assert.assertFalse(acl.canRead(User.getAnonymous(), "/"));
  }

  @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "cyclic at groupA")
  public void groupOfGroupCycle() {
    final Map<String, String[]> groups = ImmutableMap.<String, String[]>builder()
        .put("groupA", new String[]{"@groupB"})
        .put("groupB", new String[]{"@groupA"})
        .build();
    new ACL(groups, Collections.singletonMap("/", Collections.emptyMap()));
  }

  @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Group groupA references nonexistent group groupB")
  public void groupOfGroupNonexistent() {
    new ACL(Collections.singletonMap("groupA", new String[]{"@groupB"}), Collections.singletonMap("/", Collections.emptyMap()));
  }

  @Test
  public void anonymousMarker() {
    final ACL acl = new ACL(Collections.emptyMap(), Collections.singletonMap("/", Collections.singletonMap(ACL.AnonymousMarker, "r")));

    Assert.assertFalse(acl.canRead(Bob, "/"));
    Assert.assertTrue(acl.canRead(User.getAnonymous(), "/"));
  }

  @Test
  public void anonymousInGroup() {
    final ACL acl = new ACL(Collections.singletonMap("group", new String[]{"$anonymous"}), Collections.singletonMap("/", Collections.singletonMap("@group", "r")));

    Assert.assertFalse(acl.canRead(Bob, "/"));
    Assert.assertTrue(acl.canRead(User.getAnonymous(), "/"));
  }

  @Test
  public void authenticatedMarker() {
    final ACL acl = new ACL(Collections.emptyMap(), Collections.singletonMap("/", Collections.singletonMap(ACL.AuthenticatedMarker, "r")));

    Assert.assertTrue(acl.canRead(Bob, "/"));
    Assert.assertFalse(acl.canRead(User.getAnonymous(), "/"));
  }

  @Test
  public void authenticatedInGroup() {
    final ACL acl = new ACL(Collections.singletonMap("group", new String[]{"$authenticated"}), Collections.singletonMap("/", Collections.singletonMap("@group", "r")));

    Assert.assertTrue(acl.canRead(Bob, "/"));
    Assert.assertFalse(acl.canRead(User.getAnonymous(), "/"));
  }

  @Test
  public void authenticatedType() {
    final ACL acl = new ACL(Collections.singletonMap("group", new String[]{"$authenticated:GitLab"}), Collections.singletonMap("/", Collections.singletonMap("@group", "r")));

    Assert.assertFalse(acl.canRead(Bob, "/"));
    Assert.assertFalse(acl.canRead(User.getAnonymous(), "/"));
    Assert.assertTrue(acl.canRead(User.create("bla", "bla", "bla", "bla", UserType.GitLab), "/"));
  }

  @Test
  public void everyoneMarker() {
    final ACL acl = new ACL(Collections.emptyMap(), Collections.singletonMap("/", Collections.singletonMap(ACL.EveryoneMarker, "r")));

    Assert.assertTrue(acl.canRead(Bob, "/"));
    Assert.assertTrue(acl.canRead(User.getAnonymous(), "/"));
  }

  @Test
  public void rootAllow() {
    final ACL acl = new ACL(Collections.emptyMap(), Collections.singletonMap("/", Collections.singletonMap(Bob.getUserName(), "rw")));

    Assert.assertTrue(acl.canRead(Bob, "/"));
    Assert.assertFalse(acl.canRead(Alice, "/"));
    Assert.assertFalse(acl.canRead(User.getAnonymous(), "/"));

    Assert.assertTrue(acl.canRead(Bob, "/qwe"));
    Assert.assertFalse(acl.canRead(Alice, "/qwe"));
    Assert.assertFalse(acl.canRead(User.getAnonymous(), "/qwe"));

    Assert.assertTrue(acl.canRead(Bob, "/qwe/qwe"));
    Assert.assertFalse(acl.canRead(Alice, "/qwe/qwe"));
    Assert.assertFalse(acl.canRead(User.getAnonymous(), "/qwe/qwe"));
  }

  @Test
  public void deepAllow() {
    final ACL acl = new ACL(Collections.emptyMap(), Collections.singletonMap("/qwe", Collections.singletonMap(Bob.getUserName(), "rw")));

    Assert.assertFalse(acl.canRead(Bob, "/"));
    Assert.assertFalse(acl.canRead(Alice, "/"));
    Assert.assertFalse(acl.canRead(User.getAnonymous(), "/"));

    Assert.assertTrue(acl.canRead(Bob, "/qwe"));
    Assert.assertFalse(acl.canRead(Alice, "/qwe"));
    Assert.assertFalse(acl.canRead(User.getAnonymous(), "/qwe"));

    Assert.assertTrue(acl.canRead(Bob, "/qwe/qwe"));
    Assert.assertFalse(acl.canRead(Alice, "/qwe/qwe"));
    Assert.assertFalse(acl.canRead(User.getAnonymous(), "/qwe/qwe"));
  }

  @Test
  public void deepDeny() {
    final Map<String, Map<String, String>> entries = ImmutableMap.<String, Map<String, String>>builder()
        .put("/qwe", Collections.singletonMap(Bob.getUserName(), null))
        .put("/", Collections.singletonMap(Bob.getUserName(), "rw"))
        .build();

    final ACL acl = new ACL(Collections.emptyMap(), entries);

    Assert.assertTrue(acl.canRead(Bob, "/"));
    Assert.assertFalse(acl.canRead(Bob, "/qwe"));
    Assert.assertFalse(acl.canRead(Bob, "/qwe/qwe"));
  }

  /**
   * Test for #276.
   */
  @Test
  public void floorEntry() {
    final Map<String, Map<String, String>> entries = ImmutableMap.<String, Map<String, String>>builder()
        .put("/", Collections.singletonMap(Bob.getUserName(), "rw"))
        .put("/a", Collections.singletonMap(Bob.getUserName(), null))
        .build();

    final ACL acl = new ACL(Collections.emptyMap(), entries);

    Assert.assertTrue(acl.canRead(Bob, "/b"));
  }
}
