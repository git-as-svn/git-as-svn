/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.path;

import org.jetbrains.annotations.NotNull;
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
    final List<String> actual = Wildcard.splitPattern(pattern);
    Assert.assertEquals(actual.toArray(new String[actual.size()]), expected, pattern);
  }

  @DataProvider
  public static Object[][] normalizePatternData() {
    return new Object[][]{
        // Simple mask
        new Object[]{"**", new String[]{}},
        new Object[]{"**/", new String[]{"**/"}},
        new Object[]{"foo", new String[]{"**/", "foo"}},
        new Object[]{"foo/", new String[]{"foo/"}},
        new Object[]{"/foo", new String[]{"/", "foo"}},

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
    final List<String> actual = Wildcard.normalizePattern(Wildcard.splitPattern(pattern));
    Assert.assertEquals(actual.toArray(new String[actual.size()]), expected, pattern);
  }
}
