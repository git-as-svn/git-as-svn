/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.cache;

import com.google.common.cache.CacheBuilder;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNException;
import svnserver.UserType;
import svnserver.auth.User;

/**
 * Test for ReferenceHelper.
 *
 * @author Artem V. Navrotskiy
 */
public final class CacheUserDBTest {
  @Test
  public void testSimple() throws SVNException {
    User user = User.create("foo", "Foo", "foo@bar", "f01", UserType.Local);
    TestUserDB db = new TestUserDB(user);
    CacheUserDB cache = new CacheUserDB(db, CacheBuilder.newBuilder().build());

    for (int pass = 0; pass < 3; ++pass) {
      Assert.assertNull(cache.check("foo", "bar"));
      Assert.assertNull(cache.check("foo", "bar1"));
      Assert.assertEquals(cache.check("foo", db.password("foo")), user);

      Assert.assertNull(cache.lookupByUserName("foo1"));
      Assert.assertEquals(cache.lookupByUserName("foo"), user);
      Assert.assertNull(cache.lookupByUserName("foo2"));

      Assert.assertNull(cache.lookupByExternal("f00"));
      Assert.assertEquals(cache.lookupByExternal("f01"), user);
      Assert.assertNull(cache.lookupByExternal("foo"));

      Assert.assertNull(cache.check("foo", "bar1"));
      Assert.assertNull(cache.check("foo", "bar2"));
      Assert.assertEquals(cache.check("foo", db.password("foo")), user);
    }

    Assert.assertEquals(db.report(), "check: foo, bar\n" +
        "check: foo, bar1\n" +
        "check: foo, ~~~foo~~~\n" +
        "lookupByUserName: foo1\n" +
        "lookupByUserName: foo\n" +
        "lookupByUserName: foo2\n" +
        "lookupByExternal: f00\n" +
        "lookupByExternal: f01\n" +
        "lookupByExternal: foo\n" +
        "check: foo, bar2");
  }
}
