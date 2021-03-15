/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.auth.cache

import com.google.common.cache.CacheBuilder
import org.testng.Assert
import org.testng.annotations.Test
import svnserver.UserType
import svnserver.auth.User

/**
 * Test for ReferenceHelper.
 *
 * @author Artem V. Navrotskiy
 */
class CacheUserDBTest {
    @Test
    fun testSimple() {
        val user = User.create("foo", "Foo", "foo@bar", "f01", UserType.Local, null)
        val db = TestUserDB(user)
        val cache = CacheUserDB(db, CacheBuilder.newBuilder().build())
        for (pass in 0..2) {
            Assert.assertNull(cache.check("foo", "bar"))
            Assert.assertNull(cache.check("foo", "bar1"))
            Assert.assertEquals(cache.check("foo", db.password("foo")), user)
            Assert.assertNull(cache.lookupByUserName("foo1"))
            Assert.assertEquals(cache.lookupByUserName("foo"), user)
            Assert.assertNull(cache.lookupByUserName("foo2"))
            Assert.assertNull(cache.lookupByExternal("f00"))
            Assert.assertEquals(cache.lookupByExternal("f01"), user)
            Assert.assertNull(cache.lookupByExternal("foo"))
            Assert.assertNull(cache.check("foo", "bar1"))
            Assert.assertNull(cache.check("foo", "bar2"))
            Assert.assertEquals(cache.check("foo", db.password("foo")), user)
        }
        Assert.assertEquals(
            db.report(), """
     check: foo, bar
     check: foo, bar1
     check: foo, ~~~foo~~~
     lookupByUserName: foo1
     lookupByUserName: foo
     lookupByUserName: foo2
     lookupByExternal: f00
     lookupByExternal: f01
     lookupByExternal: foo
     check: foo, bar2
     """.trimIndent()
        )
    }
}
