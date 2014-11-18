/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop;

import org.eclipse.jgit.lib.FileMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for GitAttributes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitIgnoreTest {
  @Test
  public void testParseAttributes() {
    final GitProperty attr = new GitIgnore(
        "# comment\n" +
            "*.class\n" +
            "\\#*\n" +
            "/.idea/\n" +
            "deploy/\n" +
            "space end\\ \n" +
            "*/build/\n" +
            "*/.idea/vcs.xml\n" +
            "**/temp\n" +
            "**/foo/bar\n" +
            "data/**/*.sample\n"
    );
    checkProps(attr, ".idea\ndeploy\n", "*.class\n#*\nspace end \ntemp\n");
    // Rule: */.idea/vcs.xml
    checkProps(attr, "build\n", null, ".idea");
    checkProps(attr, "vcs.xml\n", null, "server", ".idea");
    // Rule: */build/
    checkProps(attr, "build\n", null, "build");
    checkProps(attr, "build\n", null, "server");
    checkProps(attr, null, null, "server", "build");
    // Rule: **/foo/bar
    checkProps(attr, "build\nbar\n", null, "foo");
    checkProps(attr, null, null, "foo", "bar");
    checkProps(attr, "bar\n", null, "server", "foo");
    checkProps(attr, null, null, "server", "foo", "bar");
    // Rule: all/**/*.sample
    checkProps(attr, "build\n", "*.sample\n", "data");
    checkProps(attr, null, null, "data", "data");
    checkProps(attr, null, null, "server", "data");
  }

  private void checkProps(@NotNull GitProperty gitProperty, @Nullable String local, @Nullable String global, @NotNull String... path) {
    GitProperty prop = gitProperty;
    for (String name : path) {
      prop = prop.createForChild(name, FileMode.TREE);
      if (prop == null && local == null && global == null) {
        return;
      }
      Assert.assertNotNull(prop);
    }
    final Map<String, String> props = new HashMap<>();
    prop.apply(props);
    Assert.assertEquals(props.get("svn:ignore"), local);
    Assert.assertEquals(props.get("svn:global-ignores"), global);
    Assert.assertEquals(props.size(), (local == null ? 0 : 1) + (global == null ? 0 : 1));
  }

  @DataProvider
  public static Object[][] testLineParseData() {
    return new Object[][]{
        // Comments
        new Object[]{"#comment", ""},
        new Object[]{"\\#comment", "#comment"},
        // Trailing space
        new Object[]{"space ", "space"},
        new Object[]{"space\\ ", "space "},
        new Object[]{"space\\ \\     ", "space  "},
        new Object[]{"    ", ""},
        // Mask begins from "**/"
        new Object[]{"**/foo/bar", "**/foo/bar"},
        new Object[]{"**/foo/", "foo"},
        new Object[]{"**/foo", "foo"},
        new Object[]{"/**/foo/bar", "**/foo/bar"},
        new Object[]{"/**/foo/", "foo"},
        new Object[]{"/**/foo", "foo"},
        new Object[]{"**/**/foo/bar", "**/foo/bar"},
        new Object[]{"**/**/foo/", "foo"},
        new Object[]{"**/**/foo", "foo"},
        new Object[]{"/**/**/foo/bar", "**/foo/bar"},
        new Object[]{"/**/**/foo/", "foo"},
        new Object[]{"/**/**/foo", "foo"},
        new Object[]{"foo/**/", "/foo"},
        new Object[]{"foo/**", "/foo"},
        new Object[]{"foo/**/**/", "/foo"},
        new Object[]{"foo/**/**", "/foo"},
        // Directories
        new Object[]{"foo", "foo"},
        new Object[]{"foo/", "/foo"},
        new Object[]{"/foo", "/foo"},
        new Object[]{"/foo/", "/foo"},
    };
  }

  @Test(dataProvider = "testLineParseData")
  public void testLineParse(@NotNull String raw, @NotNull String expected) {
    Assert.assertEquals(GitIgnore.parseLine(raw), expected);
  }
}
