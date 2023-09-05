/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth

import org.eclipse.jgit.lib.Constants
import org.testng.Assert
import org.testng.annotations.Test
import svnserver.UserType
import java.util.*

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
class ACLTest {
    @Test
    fun emptyDeny() {
        val acl = ACL(emptyMap(), emptyMap())
        Assert.assertFalse(acl.canRead(Bob, Constants.MASTER, "/"))
        Assert.assertFalse(acl.canRead(User.anonymous, Constants.MASTER, "/"))
    }

    @Test
    fun groupOfGroup() {
        val groups = hashMapOf(
            "groupOfGroup" to arrayOf("@group"),
            "group" to arrayOf(Bob.username),
        )
        val acl = ACL(groups, Collections.singletonMap("/", Collections.singletonMap("@groupOfGroup", "r")))
        Assert.assertTrue(acl.canRead(Bob, Constants.MASTER, "/"))
        Assert.assertFalse(acl.canRead(User.anonymous, Constants.MASTER, "/"))
    }

    @Test
    fun groupOfGroupOfGroup() {
        val groups = hashMapOf(
            "groupOfGroupOfGroup" to arrayOf("@groupOfGroup"),
            "groupOfGroup" to arrayOf("@group"),
            "group" to arrayOf(Bob.username),
        )
        val acl = ACL(groups, Collections.singletonMap("/", Collections.singletonMap("@groupOfGroupOfGroup", "r")))
        Assert.assertTrue(acl.canRead(Bob, Constants.MASTER, "/"))
        Assert.assertFalse(acl.canRead(User.anonymous, Constants.MASTER, "/"))
    }

    @Test(expectedExceptions = [IllegalStateException::class], expectedExceptionsMessageRegExp = "cyclic at groupA")
    fun groupOfGroupCycle() {
        val groups = hashMapOf(
            "groupA" to arrayOf("@groupB"),
            "groupB" to arrayOf("@groupA"),
        )
        ACL(groups, Collections.singletonMap("/", emptyMap()))
    }

    @Test(expectedExceptions = [IllegalArgumentException::class], expectedExceptionsMessageRegExp = ".*Group groupA references nonexistent group groupB")
    fun groupOfGroupNonexistent() {
        ACL(Collections.singletonMap("groupA", arrayOf("@groupB")), Collections.singletonMap("/", emptyMap()))
    }

    @Test(expectedExceptions = [IllegalArgumentException::class], expectedExceptionsMessageRegExp = ".*Branch name.*empty.*")
    fun emptyBranchName() {
        ACL(emptyMap(), Collections.singletonMap(":/", emptyMap()))
    }

    @Test(expectedExceptions = [IllegalArgumentException::class], expectedExceptionsMessageRegExp = ".*Path in ACL entry must start with slash.*")
    fun branchEmptyPath() {
        ACL(emptyMap(), Collections.singletonMap("branch:", emptyMap()))
    }

    @Test(expectedExceptions = [IllegalArgumentException::class], expectedExceptionsMessageRegExp = ".*Path in ACL entry must not end with slash.*")
    fun pathSlashEnd() {
        ACL(emptyMap(), Collections.singletonMap("/bla/", emptyMap()))
    }

    @Test
    fun branchAllow() {
        val acl = ACL(emptyMap(), Collections.singletonMap("master:/", Collections.singletonMap(Bob.username, "rw")))
        Assert.assertTrue(acl.canRead(Bob, "master", "/"))
        Assert.assertFalse(acl.canRead(Bob, "release", "/"))
    }

    @Test
    fun branchDeny() {
        val entries = hashMapOf(
            "master:/" to Collections.singletonMap<String, String?>(Bob.username, null),
            "/" to Collections.singletonMap(Bob.username, "rw"),
        )
        val acl = ACL(emptyMap(), entries)
        Assert.assertFalse(acl.canRead(Bob, "master", "/"))
        Assert.assertTrue(acl.canRead(Bob, "release", "/"))
    }

    @Test
    fun anonymousMarker() {
        val acl = ACL(emptyMap(), Collections.singletonMap("/", Collections.singletonMap(ACL.AnonymousMarker, "r")))
        Assert.assertFalse(acl.canRead(Bob, Constants.MASTER, "/"))
        Assert.assertTrue(acl.canRead(User.anonymous, Constants.MASTER, "/"))
    }

    @Test
    fun anonymousInGroup() {
        val acl = ACL(Collections.singletonMap("group", arrayOf("\$anonymous")), Collections.singletonMap("/", Collections.singletonMap("@group", "r")))
        Assert.assertFalse(acl.canRead(Bob, Constants.MASTER, "/"))
        Assert.assertTrue(acl.canRead(User.anonymous, Constants.MASTER, "/"))
    }

    @Test
    fun authenticatedMarker() {
        val acl = ACL(emptyMap(), Collections.singletonMap("/", Collections.singletonMap(ACL.AuthenticatedMarker, "r")))
        Assert.assertTrue(acl.canRead(Bob, Constants.MASTER, "/"))
        Assert.assertFalse(acl.canRead(User.anonymous, Constants.MASTER, "/"))
    }

    @Test
    fun authenticatedInGroup() {
        val acl = ACL(Collections.singletonMap("group", arrayOf("\$authenticated")), Collections.singletonMap("/", Collections.singletonMap("@group", "r")))
        Assert.assertTrue(acl.canRead(Bob, Constants.MASTER, "/"))
        Assert.assertFalse(acl.canRead(User.anonymous, Constants.MASTER, "/"))
    }

    @Test
    fun authenticatedType() {
        val acl = ACL(Collections.singletonMap("group", arrayOf("\$authenticated:GitLab")), Collections.singletonMap("/", Collections.singletonMap("@group", "r")))
        Assert.assertFalse(acl.canRead(Bob, Constants.MASTER, "/"))
        Assert.assertFalse(acl.canRead(User.anonymous, Constants.MASTER, "/"))
        Assert.assertTrue(acl.canRead(User.create("bla", "bla", "bla", "bla", UserType.GitLab, null), Constants.MASTER, "/"))
    }

    @Test
    fun everyoneMarker() {
        val acl = ACL(emptyMap(), Collections.singletonMap("/", Collections.singletonMap(ACL.EveryoneMarker, "r")))
        Assert.assertTrue(acl.canRead(Bob, Constants.MASTER, "/"))
        Assert.assertTrue(acl.canRead(User.anonymous, Constants.MASTER, "/"))
    }

    @Test
    fun rootAllow() {
        val acl = ACL(emptyMap(), Collections.singletonMap("/", Collections.singletonMap(Bob.username, "rw")))
        Assert.assertTrue(acl.canRead(Bob, Constants.MASTER, "/"))
        Assert.assertFalse(acl.canRead(Alice, Constants.MASTER, "/"))
        Assert.assertFalse(acl.canRead(User.anonymous, Constants.MASTER, "/"))
        Assert.assertTrue(acl.canRead(Bob, Constants.MASTER, "/qwe"))
        Assert.assertFalse(acl.canRead(Alice, Constants.MASTER, "/qwe"))
        Assert.assertFalse(acl.canRead(User.anonymous, Constants.MASTER, "/qwe"))
        Assert.assertTrue(acl.canRead(Bob, Constants.MASTER, "/qwe/qwe"))
        Assert.assertFalse(acl.canRead(Alice, Constants.MASTER, "/qwe/qwe"))
        Assert.assertFalse(acl.canRead(User.anonymous, Constants.MASTER, "/qwe/qwe"))
    }

    @Test
    fun deepAllow() {
        val acl = ACL(emptyMap(), Collections.singletonMap("/qwe", Collections.singletonMap(Bob.username, "rw")))
        Assert.assertFalse(acl.canRead(Bob, Constants.MASTER, "/"))
        Assert.assertFalse(acl.canRead(Alice, Constants.MASTER, "/"))
        Assert.assertFalse(acl.canRead(User.anonymous, Constants.MASTER, "/"))
        Assert.assertTrue(acl.canRead(Bob, Constants.MASTER, "/qwe"))
        Assert.assertFalse(acl.canRead(Alice, Constants.MASTER, "/qwe"))
        Assert.assertFalse(acl.canRead(User.anonymous, Constants.MASTER, "/qwe"))
        Assert.assertTrue(acl.canRead(Bob, Constants.MASTER, "/qwe/qwe"))
        Assert.assertFalse(acl.canRead(Alice, Constants.MASTER, "/qwe/qwe"))
        Assert.assertFalse(acl.canRead(User.anonymous, Constants.MASTER, "/qwe/qwe"))
    }

    @Test
    fun deepDeny() {
        val entries = hashMapOf(
            "/qwe" to Collections.singletonMap<String, String?>(Bob.username, null),
            "/" to Collections.singletonMap(Bob.username, "rw"),
        )
        val acl = ACL(emptyMap(), entries)
        Assert.assertTrue(acl.canRead(Bob, Constants.MASTER, "/"))
        Assert.assertFalse(acl.canRead(Bob, Constants.MASTER, "/qwe"))
        Assert.assertFalse(acl.canRead(Bob, Constants.MASTER, "/qwe/qwe"))
    }

    /**
     * Test for #276.
     */
    @Test
    fun floorEntry() {
        val entries = hashMapOf(
            "/" to Collections.singletonMap(Bob.username, "rw"),
            "/a" to Collections.singletonMap<String, String?>(Bob.username, null),
        )
        val acl = ACL(emptyMap(), entries)
        Assert.assertTrue(acl.canRead(Bob, Constants.MASTER, "/b"))
    }

    companion object {
        private val Bob = User.create("bob", "Bob", "bob@acme.com", null, UserType.Local, null)
        private val Alice = User.create("alice", "Alice", "alice@acme.com", null, UserType.Local, null)
    }
}
