package svnserver.repository.git.prop;

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
    checkProps(attr, ".idea\ndeploy", "*.class\n#*\nspace end \ntemp");
    // Rule: */.idea/vcs.xml
    checkProps(attr, "build", null, ".idea");
    checkProps(attr, "vcs.xml", null, "server", ".idea");
    // Rule: */build/
    checkProps(attr, "build", null, "build");
    checkProps(attr, "build", null, "server");
    checkProps(attr, null, null, "server", "build");
    // Rule: **/foo/bar
    checkProps(attr, "build\nbar", null, "foo");
    checkProps(attr, null, null, "foo", "bar");
    checkProps(attr, "bar", null, "server", "foo");
    checkProps(attr, null, null, "server", "foo", "bar");
    // Rule: data/**/*.sample
    checkProps(attr, "build", "*.sample", "data");
    checkProps(attr, null, null, "data", "data");
    checkProps(attr, null, null, "server", "data");
  }

  private void checkProps(@NotNull GitProperty gitProperty, @Nullable String local, @Nullable String global, @NotNull String... path) {
    GitProperty prop = gitProperty;
    for (String name : path) {
      prop = prop.createForChild(name);
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
