/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop

import org.testng.Assert
import org.testng.annotations.Test
import svnserver.TestHelper
import java.io.IOException
import java.util.*

/**
 * Tests for GitAttributes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitTortoiseTest {
    @Test
    @Throws(IOException::class)
    fun testParseAttributes() {
        val attr: GitProperty
        TestHelper.asStream(
            """[bugtraq]
	url = http://bugtracking/browse/%BUGID%
	logregex = (BUG-\\d+)
	warnifnoissue = false"""
        ).use { `in` -> attr = GitTortoise.parseConfig(`in`) }
        val props = HashMap<String, String>()
        attr.apply(props)
        Assert.assertEquals(props.size, 3)
        Assert.assertEquals(props["bugtraq:url"], "http://bugtracking/browse/%BUGID%")
        Assert.assertEquals(props["bugtraq:logregex"], "(BUG-\\d+)")
        Assert.assertEquals(props["bugtraq:warnifnoissue"], "false")
    }

    @Test
    @Throws(IOException::class)
    fun testTortoiseAttributes() {
        val attr: GitProperty
        TestHelper.asStream(
            """[bugtraq]
	url = https://tortoisegit.org/issue/%BUGID%
	logregex = "[Ii]ssues?:?(\\s*(,|and)?\\s*#?\\d+)+\n(\\d+)"
	warnifnoissue = false

[tgit]
	warnnosignedoffby = true
	projectlanguage = 1033
	icon = src/Resources/Tortoise.ico"""
        ).use { `in` -> attr = GitTortoise.parseConfig(`in`) }
        val props = HashMap<String, String>()
        attr.apply(props)
        Assert.assertEquals(props.size, 6)
        Assert.assertEquals(props["bugtraq:url"], "https://tortoisegit.org/issue/%BUGID%")
        Assert.assertEquals(props["bugtraq:logregex"], "[Ii]ssues?:?(\\s*(,|and)?\\s*#?\\d+)+\n(\\d+)")
        Assert.assertEquals(props["bugtraq:warnifnoissue"], "false")
        Assert.assertEquals(props["tgit:warnnosignedoffby"], "true")
        Assert.assertEquals(props["tgit:projectlanguage"], "1033")
        Assert.assertEquals(props["tgit:icon"], "src/Resources/Tortoise.ico")
    }
}
