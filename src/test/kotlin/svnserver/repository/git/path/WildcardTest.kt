/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.path

import org.testng.Assert
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import svnserver.repository.git.path.WildcardHelper.nameMatcher
import svnserver.repository.git.path.WildcardHelper.normalizePattern
import svnserver.repository.git.path.WildcardHelper.splitPattern
import svnserver.repository.git.path.WildcardHelper.tryRemoveBackslashes

/**
 * Test wildcard parsing.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
object WildcardTest {
    @DataProvider
    fun splitPatternData(): Array<Array<Any>> {
        return arrayOf(arrayOf("foo", arrayOf("foo")), arrayOf("foo/", arrayOf("foo/")), arrayOf("/bar", arrayOf("/", "bar")), arrayOf("/foo/bar/**", arrayOf("/", "foo/", "bar/", "**")))
    }

    @Test(dataProvider = "splitPatternData")
    fun splitPatternTest(pattern: String, expected: Array<String>) {
        val actual: List<String> = splitPattern(pattern)
        Assert.assertEquals(actual.toTypedArray(), expected, pattern)
    }

    @DataProvider
    fun normalizePatternData(): Array<Array<out Any>> {
        return arrayOf(
            arrayOf("/", emptyArray<String>()),
            arrayOf("*/", arrayOf("*/")),
            arrayOf("*", arrayOf<String>()),
            arrayOf("**", arrayOf<String>()),
            arrayOf("**/", arrayOf<String>()),
            arrayOf("foo", arrayOf("**/", "foo")),
            arrayOf("foo/", arrayOf("foo/")),
            arrayOf("/foo", arrayOf("foo")),
            arrayOf("foo/**.bar", arrayOf("foo/", "**/", "*.bar")),
            arrayOf("foo/***.bar", arrayOf("foo/", "**/", "*.bar")),
            arrayOf("foo/*/bar", arrayOf("foo/", "*/", "bar")),
            arrayOf("foo/**/bar", arrayOf("foo/", "**/", "bar")),
            arrayOf("foo/*/*/bar", arrayOf("foo/", "*/", "*/", "bar")),
            arrayOf("foo/**/*/bar", arrayOf("foo/", "*/", "**/", "bar")),
            arrayOf("foo/*/**/bar", arrayOf("foo/", "*/", "**/", "bar")),
            arrayOf("foo/*/**.bar", arrayOf("foo/", "*/", "**/", "*.bar")),
            arrayOf("foo/**/**/bar", arrayOf("foo/", "**/", "bar")),
            arrayOf("foo/**/**.bar", arrayOf("foo/", "**/", "*.bar")),
            arrayOf("foo/**/*/**/*/bar", arrayOf("foo/", "*/", "*/", "**/", "bar")),
            arrayOf("foo/**/*/**/*/**.bar", arrayOf("foo/", "*/", "*/", "**/", "*.bar")),
            arrayOf("foo/**", arrayOf("foo/")),
            arrayOf("foo/**/*", arrayOf("foo/")),
            arrayOf("foo/**/*/*", arrayOf("foo/", "*/")),
            arrayOf("foo/**/", arrayOf("foo/")),
            arrayOf("foo/**/*/", arrayOf("foo/", "*/")),
            arrayOf("foo/**/*/*/", arrayOf("foo/", "*/", "*/"))
        )
    }

    @Test(dataProvider = "normalizePatternData")
    fun normalizePatternTest(pattern: String, expected: Array<String>?) {
        val actual = normalizePattern(splitPattern(pattern))
        Assert.assertTrue(actual.size > 0)
        Assert.assertEquals(actual.removeAt(0), "/")
        Assert.assertEquals(actual.toTypedArray(), expected, pattern)
    }

    @DataProvider
    fun nameMatcherData(): Array<Array<Any?>> {
        return arrayOf(arrayOf("*", false, "*"), arrayOf("*/", false, "*"), arrayOf("foo*.bar", false, "foo*.bar"), arrayOf("foo*.bar/", false, "foo*.bar"), arrayOf("foo*buzz*.bar", false, "foo*buzz*.bar"), arrayOf("fo[oO]*.bar", false, null), arrayOf("sample", false, "sample"), arrayOf("sample/", false, "sample"), arrayOf("**/", true, null))
    }

    @Test(dataProvider = "nameMatcherData")
    fun nameMatcherTest(mask: String, recursive: Boolean, svnMask: String?) {
        val matcher = nameMatcher(mask)
        Assert.assertEquals(matcher.isRecursive, recursive)
        Assert.assertEquals(svnMask, matcher.svnMask)
    }

    @DataProvider
    fun pathMatcherData(): Array<Array<Any?>> {
        return arrayOf(
            arrayOf("/", "foo/bar", true),
            arrayOf("*", "foo/bar", true),
            arrayOf("*/", "foo/bar", true),
            arrayOf("/", "foo/bar/", true),
            arrayOf("*", "foo/bar/", true),
            arrayOf("*/", "foo/bar/", true),
            arrayOf("**/", "foo/bar/", true),
            arrayOf("foo/**/", "foo/bar/", true),
            arrayOf("foo/**/", "foo/bar/xxx", true),
            arrayOf("foo/**/", "foo/bar/xxx/", true),
            arrayOf("f*o", "foo/bar", true),
            arrayOf("/f*o", "foo/bar", true),
            arrayOf("f*o/", "foo/bar", true),
            arrayOf("foo/", "foo/bar", true),
            arrayOf("/foo/", "foo/bar", true),
            arrayOf("/foo", "foo/", true),
            arrayOf("foo", "foo/", true),
            arrayOf("foo/", "foo/", true),
            arrayOf("foo/", "foo", null),
            arrayOf("bar", "foo/bar", true),
            arrayOf("b*r", "foo/bar", true),
            arrayOf("/bar", "foo/bar", null),
            arrayOf("bar/", "foo/bar", null),
            arrayOf("b*r/", "foo/bar", null),
            arrayOf("bar/", "foo/bar/", null),
            arrayOf("b*r/", "foo/bar/", null),
            arrayOf("b[a-z]r", "foo/bar", true),
            arrayOf("b[a-z]r", "foo/b0r", null),
            arrayOf("b[a-z]r", "foo/b0r/", false),
            arrayOf("/t*e*t", "test", true),
            arrayOf("foo/*/bar/", "foo/bar/", false),
            arrayOf("foo/*/bar/", "bar/", null),
            arrayOf("foo/*/bar/", "foo/a/bar/", true),
            arrayOf("foo/*/bar/", "foo/a/b/bar/", null),
            arrayOf("foo/*/*/bar/", "foo/a/b/bar/", true),
            arrayOf("foo/**/bar/a/", "foo/bar/b/bar/a/", true),
            arrayOf("foo/**/bar/a/", "foo/bar/bar/bar/a/", true),
            arrayOf("foo/**/bar/a/", "foo/bar/bar/b/a/", false),
            arrayOf("foo/**/bar/", "foo/bar/", true),
            arrayOf("foo/**/bar/", "bar/", null),
            arrayOf("foo/**/bar/", "foo/a/bar/", true),
            arrayOf("foo/**/bar/", "foo/a/b/bar/", true),
            arrayOf("foo/*/**/*/bar/", "foo/a/bar/", false),
            arrayOf("foo/*/**/*/bar/", "foo/a/b/bar/", true),
            arrayOf("foo/*/**/*/bar/", "foo/a/b/c/bar/", true),
            arrayOf("foo/**/xxx/**/bar/", "foo/xxx/bar/", true),
            arrayOf("foo/**/xxx/**/bar/", "foo/xxx/b/c/bar/", true),
            arrayOf("foo/**/xxx/**/bar/", "foo/a/xxx/c/bar/", true),
            arrayOf("foo/**/xxx/**/bar/", "foo/a/c/xxx/bar/", true),
            arrayOf("foo/**/xxx/**/bar/", "foo/bar/xxx/", false),
            arrayOf("foo/**/xxx/**/bar/", "foo/bar/xxx/bar/", true),
            arrayOf("foo/**/xxx/**/bar/", "foo/bar/xxx/xxx/bar/", true)
        )
    }

    @Test(dataProvider = "pathMatcherData")
    fun pathMatcherTest(pattern: String, path: String, expectedMatch: Boolean?) {
        val wildcard = Wildcard(pattern)
        var matcher: PathMatcher? = wildcard.matcher
        for (name in splitPattern(path)) {
            if (matcher == null) break
            val isDir = name.endsWith("/")
            matcher = matcher.createChild(if (isDir) name.substring(0, name.length - 1) else name, isDir)
        }
        if (expectedMatch == null) {
            Assert.assertNull(matcher)
        } else {
            Assert.assertNotNull(matcher)
            Assert.assertEquals(matcher!!.isMatch, expectedMatch)
        }
    }

    @DataProvider
    fun tryRemoveBackslashesData(): Array<Array<Any>> {
        return arrayOf(arrayOf("test", "test"), arrayOf("test\\n", "test\\n"), arrayOf("space\\ ", "space "), arrayOf("foo\\!bar\\ ", "foo!bar "), arrayOf("\\#some", "#some"), arrayOf("foo\\[bar", "foo\\[bar"))
    }

    @Test(dataProvider = "tryRemoveBackslashesData")
    fun tryRemoveBackslashesTest(pattern: String, expected: String) {
        Assert.assertEquals(tryRemoveBackslashes(pattern), expected)
    }
}
