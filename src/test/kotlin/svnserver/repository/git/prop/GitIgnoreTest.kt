/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop

import org.eclipse.jgit.lib.FileMode
import org.testng.Assert
import org.testng.annotations.Test
import svnserver.TestHelper

/**
 * Tests for GitAttributes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitIgnoreTest {
    @Test
    fun testParseAttributes() {
        val attr: GitProperty
        TestHelper.asStream(
            """# comment
*.class
\#*
/.idea/
deploy/
space end\ 
*/build/
*/.idea/vcs.xml
**/temp
**/foo/bar
qqq/**/bar
data/**/*.sample
"""
        ).use { `in` -> attr = GitIgnore.parseConfig(`in`) }
        checkProps(attr, ".idea\ndeploy\n", "*.class\n#*\nspace end \ntemp\n")
        // Rule: */.idea/vcs.xml
        checkProps(attr, "build\n", null, ".idea\ndeploy")
        checkProps(attr, "vcs.xml\n", null, "server", ".idea")
        // Rule: */build/
        checkProps(attr, "build\n", null, "build")
        checkProps(attr, "build\n", null, "server")
        checkProps(attr, null, "*\n", "server", "build")
        checkProps(attr, null, null, "server", "build", "data")
        checkProps(attr, null, null, "server", "data", "build")
        checkProps(attr, null, "*\n", "deploy", "build")
        // Rule: **/foo/bar
        checkProps(attr, "build\nbar\n", null, "foo")
        checkProps(attr, null, "*\n", "foo", "bar")
        checkProps(attr, "bar\n", null, "server", "foo")
        checkProps(attr, null, "*\n", "server", "foo", "bar")
        checkProps(attr, null, "*\n", "qqq", "foo", "bar")
        // Rule: all/**/*.sample
        checkProps(attr, "build\n", "*.sample\n", "data")
        checkProps(attr, null, null, "data", "data")
        checkProps(attr, null, null, "server", "data")
    }

    private fun checkProps(gitProperty: GitProperty, local: String?, global: String?, vararg path: String) {
        var prop: GitProperty? = gitProperty
        for (name in path) {
            prop = prop!!.createForChild(name, FileMode.TREE)
            if (prop == null && local == null && global == null) {
                return
            }
            Assert.assertNotNull(prop)
        }
        val props = HashMap<String, String>()
        prop!!.apply(props)
        Assert.assertEquals(props["svn:ignore"], local)
        Assert.assertEquals(props["svn:global-ignores"], global)
        Assert.assertEquals(props.size, (if (local == null) 0 else 1) + if (global == null) 0 else 1)
    }
}
