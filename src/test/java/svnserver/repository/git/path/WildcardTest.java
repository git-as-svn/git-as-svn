/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.path;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Test wildcard parsing.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class WildcardTest {
  @DataProvider
  public static Object[][] splitPatternData() {
    return new Object[][]{
        new Object[]{"foo", new String[]{"foo"}},
        new Object[]{"foo/", new String[]{"foo/"}},
        new Object[]{"/bar", new String[]{"/", "bar"}},
        new Object[]{"/foo/bar/**", new String[]{"/", "foo/", "bar/", "**"}},
    };
  }

  @Test(dataProvider = "splitPatternData")
  public static void splitPatternTest(@NotNull String pattern, @NotNull String[] expected) {
    final List<String> actual = WildcardHelper.splitPattern(pattern);
    Assert.assertEquals(actual.toArray(new String[actual.size()]), expected, pattern);
  }

  @DataProvider
  public static Object[][] normalizePatternData() {
    return new Object[][]{
        // Simple mask
        new Object[]{"/", new String[]{"**/"}},
        new Object[]{"*/", new String[]{"*/", "**/"}},
        new Object[]{"*", new String[]{"*"}},
        new Object[]{"**", new String[]{}},
        new Object[]{"**/", new String[]{"**/"}},
        new Object[]{"foo", new String[]{"**/", "foo"}},
        new Object[]{"foo/", new String[]{"**/", "foo/"}},
        new Object[]{"/foo", new String[]{"foo"}},

        // Convert path file mask
        new Object[]{"foo/**.bar", new String[]{"foo/", "**/", "*.bar"}},
        new Object[]{"foo/***.bar", new String[]{"foo/", "**/", "*.bar"}},

        // Collapse and reorder adjacent masks
        new Object[]{"foo/*/bar", new String[]{"foo/", "*/", "bar"}},
        new Object[]{"foo/**/bar", new String[]{"foo/", "**/", "bar"}},
        new Object[]{"foo/*/*/bar", new String[]{"foo/", "*/", "*/", "bar"}},
        new Object[]{"foo/**/*/bar", new String[]{"foo/", "*/", "**/", "bar"}},
        new Object[]{"foo/*/**/bar", new String[]{"foo/", "*/", "**/", "bar"}},
        new Object[]{"foo/*/**.bar", new String[]{"foo/", "*/", "**/", "*.bar"}},
        new Object[]{"foo/**/**/bar", new String[]{"foo/", "**/", "bar"}},
        new Object[]{"foo/**/**.bar", new String[]{"foo/", "**/", "*.bar"}},
        new Object[]{"foo/**/*/**/*/bar", new String[]{"foo/", "*/", "*/", "**/", "bar"}},
        new Object[]{"foo/**/*/**/*/**.bar", new String[]{"foo/", "*/", "*/", "**/", "*.bar"}},

        // Collapse trailing masks
        new Object[]{"foo/**", new String[]{"foo/"}},
        new Object[]{"foo/**/*", new String[]{"foo/", "*"}},
        new Object[]{"foo/**/*/*", new String[]{"foo/", "*/", "*"}},
        new Object[]{"foo/**/", new String[]{"foo/", "**/"}},
        new Object[]{"foo/**/*/", new String[]{"foo/", "*/", "**/"}},
        new Object[]{"foo/**/*/*/", new String[]{"foo/", "*/", "*/", "**/"}},
    };
  }

  @Test(dataProvider = "normalizePatternData")
  public static void normalizePatternTest(@NotNull String pattern, @NotNull String[] expected) {
    final List<String> actual = WildcardHelper.normalizePattern(WildcardHelper.splitPattern(pattern));
    Assert.assertTrue(actual.size() > 0);
    Assert.assertEquals(actual.remove(0), "/");
    Assert.assertEquals(actual.toArray(new String[actual.size()]), expected, pattern);
  }

  @DataProvider
  public static Object[][] nameMatcherData() {
    return new Object[][]{
        new Object[]{"*", false, "*"},
        new Object[]{"*/", false, "*"},
        new Object[]{"foo*.bar", false, "foo*.bar"},
        new Object[]{"foo*.bar/", false, "foo*.bar"},
        new Object[]{"foo*buzz*.bar", false, "foo*buzz*.bar"},
        new Object[]{"fo[oO]*.bar", false, null},
        new Object[]{"sample", false, "sample"},
        new Object[]{"sample/", false, "sample"},
        new Object[]{"**/", true, null},
    };
  }

  @Test(dataProvider = "nameMatcherData")
  public static void nameMatcherTest(@NotNull String mask, boolean recursive, @Nullable String svnMask) throws InvalidPatternException {
    final NameMatcher matcher = WildcardHelper.nameMatcher(mask);
    Assert.assertEquals(matcher.isRecursive(), recursive);
    Assert.assertEquals(svnMask, matcher.getSvnMask());
  }

  @DataProvider
  public static Object[][] pathMatcherData() {
    return new Object[][]{
        // Simple pattern
        new Object[]{"/", "foo/bar", null},
        new Object[]{"*", "foo/bar", true},
        new Object[]{"*/", "foo/bar", null},
        new Object[]{"/", "foo/bar/", true},
        new Object[]{"*", "foo/bar/", true},
        new Object[]{"*/", "foo/bar/", true},
        new Object[]{"**/", "foo/bar/", true},
        new Object[]{"foo/**/", "foo/bar/", true},
        new Object[]{"foo/**/", "foo/bar/xxx", null},
        new Object[]{"foo/**/", "foo/bar/xxx/", true},
        new Object[]{"f*o", "foo/bar", true},
        new Object[]{"/f*o", "foo/bar", true},
        new Object[]{"f*o/", "foo/bar", true},
        new Object[]{"foo/", "foo/bar", true},
        new Object[]{"/foo/", "foo/bar", true},
        new Object[]{"/foo", "foo/", true},
        new Object[]{"foo", "foo/", true},
        new Object[]{"foo/", "foo/", true},
        new Object[]{"foo/", "foo", false},
        new Object[]{"bar", "foo/bar", true},
        new Object[]{"b*r", "foo/bar", true},
        new Object[]{"/bar", "foo/bar", null},
        new Object[]{"bar/", "foo/bar", false},
        new Object[]{"b*r/", "foo/bar", false},
        new Object[]{"bar/", "foo/bar/", true},
        new Object[]{"b*r/", "foo/bar/", true},
        new Object[]{"b[a-z]r", "foo/bar", true},
        new Object[]{"b[a-z]r", "foo/b0r", false},
        new Object[]{"/t*e*t", "test", true},
        // More complex pattern
        new Object[]{"foo/*/bar/", "foo/bar/", false},
        new Object[]{"foo/*/bar/", "bar/", null},
        new Object[]{"foo/*/bar/", "foo/a/bar/", true},
        new Object[]{"foo/*/bar/", "foo/a/b/bar/", null},
        new Object[]{"foo/*/*/bar/", "foo/a/b/bar/", true},

        new Object[]{"foo/**/bar/a/", "foo/bar/b/bar/a/", true},
        new Object[]{"foo/**/bar/a/", "foo/bar/bar/bar/a/", true},
        new Object[]{"foo/**/bar/a/", "foo/bar/bar/b/a/", false},
        new Object[]{"foo/**/bar/", "foo/bar/", true},
        new Object[]{"foo/**/bar/", "bar/", null},
        new Object[]{"foo/**/bar/", "foo/a/bar/", true},
        new Object[]{"foo/**/bar/", "foo/a/b/bar/", true},
        new Object[]{"foo/*/**/*/bar/", "foo/a/bar/", false},
        new Object[]{"foo/*/**/*/bar/", "foo/a/b/bar/", true},
        new Object[]{"foo/*/**/*/bar/", "foo/a/b/c/bar/", true},
        new Object[]{"foo/**/xxx/**/bar/", "foo/xxx/bar/", true},
        new Object[]{"foo/**/xxx/**/bar/", "foo/xxx/b/c/bar/", true},
        new Object[]{"foo/**/xxx/**/bar/", "foo/a/xxx/c/bar/", true},
        new Object[]{"foo/**/xxx/**/bar/", "foo/a/c/xxx/bar/", true},
        new Object[]{"foo/**/xxx/**/bar/", "foo/bar/xxx/", false},
        new Object[]{"foo/**/xxx/**/bar/", "foo/bar/xxx/bar/", true},
        new Object[]{"foo/**/xxx/**/bar/", "foo/bar/xxx/xxx/bar/", true},
    };
  }

  @Test(dataProvider = "pathMatcherData")
  public static void pathMatcherTest(@NotNull String pattern, @NotNull String path, @Nullable Boolean expectedMatch) throws InvalidPatternException {
    final Wildcard wildcard = new Wildcard(pattern);
    PathMatcher matcher = wildcard.getMatcher();
    for (String name : WildcardHelper.splitPattern(path)) {
      if (matcher == null) break;
      boolean isDir = name.endsWith("/");
      matcher = matcher.createChild(isDir ? name.substring(0, name.length() - 1) : name, isDir);
    }
    if (expectedMatch == null) {
      Assert.assertNull(matcher);
    } else {
      Assert.assertNotNull(matcher);
      Assert.assertEquals(matcher.isMatch(), expectedMatch.booleanValue());
    }
  }

  @DataProvider
  public static Object[][] tryRemoveBackslashesData() {
    return new Object[][]{
        new Object[]{"test", "test"},
        new Object[]{"test\\n", "test\\n"},
        new Object[]{"space\\ ", "space "},
        new Object[]{"foo\\!bar\\ ", "foo!bar "},
        new Object[]{"\\#some", "#some"},
        new Object[]{"foo\\[bar", "foo\\[bar"},
    };
  }

  @Test(dataProvider = "tryRemoveBackslashesData")
  public static void tryRemoveBackslashesTest(@NotNull String pattern, @NotNull String expected) {
    Assert.assertEquals(WildcardHelper.tryRemoveBackslashes(pattern), expected);
  }
}
