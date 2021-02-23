/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.mapping

import org.testng.*
import org.testng.annotations.*
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNURL
import svnserver.SvnTestServer
import svnserver.repository.RepositoryMapping
import java.util.*

/**
 * Simple test for repository mapping.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class RepositoryListMappingTest {
    @Test
    @Throws(Exception::class)
    fun repoRootRelocate() {
        SvnTestServer.createEmpty().use { server ->
            val url: SVNURL = server.getUrl(false)
            try {
                SvnTestServer.openSvnRepository(url, SvnTestServer.USER_NAME, SvnTestServer.PASSWORD).latestRevision
            } catch (e: SVNException) {
                Assert.assertEquals(e.errorMessage.errorCode, SVNErrorCode.RA_SVN_REPOS_NOT_FOUND)
                val expected = String.format("Repository branch not found. Use `svn relocate %s/master` to fix your working copy", url.toString())
                Assert.assertEquals(e.errorMessage.messageTemplate, expected)
            }
        }
    }

    @Test
    fun testEmpty() {
        val map = Builder()
            .add("/")
            .build()
        checkMapped(map, "/", "")
        checkMapped(map, "/", "/foo")
        checkMapped(map, "/", "/bar")
    }

    private fun checkMapped(map: NavigableMap<String, String>, expected: String?, prefix: String) {
        val entry = RepositoryMapping.getMapped(map, prefix)
        if (expected == null) {
            Assert.assertNull(entry, prefix)
        } else {
            Assert.assertNotNull(entry, prefix)
            Assert.assertEquals(expected, entry!!.key, prefix)
        }
    }

    @Test
    fun testRepositoryByPrefix() {
        val map = Builder()
            .add("/foo/")
            .add("/bar/")
            .add("/foo.test/")
            .build()
        checkMapped(map, null, "")
        checkMapped(map, null, "/bazz")
        checkMapped(map, null, "/foo2")
        checkMapped(map, null, "/bar2")
        checkMapped(map, "/foo/", "/foo")
        checkMapped(map, "/foo/", "/foo/bar")
        checkMapped(map, "/bar/", "/bar")
        checkMapped(map, "/bar/", "/bar/foo")
        checkMapped(map, "/foo.test/", "/foo.test")
        checkMapped(map, "/foo.test/", "/foo.test/foo")
    }

    class Builder {
        private val mapping = TreeMap<String, String>()
        fun add(prefix: String): Builder {
            mapping[prefix] = prefix
            return this
        }

        fun build(): TreeMap<String, String> {
            return TreeMap(mapping)
        }
    }
}
