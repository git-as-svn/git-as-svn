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
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tests for GitAttributes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitEolTest {
  @DataProvider(name = "parseAttributesData")
  public static Object[][] parseAttributesData() throws IOException {
    final GitProperty[] attr = new GitAttributesFactory().create(
        "# comment\n" +
            "*.txt  eol=native\n" +
            "*.md   eol=lf\n" +
            "*.dat  -text\n" +
            "3.md   -text\n" +
            "*.bin  binary\n" +
            "1.bin  -binary\n" +
            "2.bin  text\n"
    );
    final Params[] params = new Params[]{
        new Params(attr, "/").prop(SVNProperty.INHERITABLE_AUTO_PROPS, "*.txt = svn:eol-style=native\n" +
            "*.md = svn:eol-style=LF\n" +
            "*.bin = svn:mime-type=application/octet-stream\n"),
        new Params(attr, "README.md").prop(SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_LF),
        new Params(attr, "foo.dat"),
        new Params(attr, "foo.txt").prop(SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_NATIVE),
        new Params(attr, "foo.bin").prop(SVNProperty.MIME_TYPE,  SVNFileUtil.BINARY_MIME_TYPE),

        new Params(attr, "1.bin"),
        new Params(attr, "2.bin"),
        new Params(attr, "3.md"),
        //new Params(attr, "changelog").prop(SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_NATIVE),
    };
    final Object[][] result = new Object[params.length][];
    for (int i = 0; i < params.length; ++i) {
      result[i] = new Object[]{params[i]};
    }
    return result;
  }

  @Test(dataProvider = "parseAttributesData")
  public void parseAttributes(@NotNull Params params) throws IOException {
    params.check();
  }

  private void checkProps(@NotNull GitProperty[] gitProperties, @Nullable String local, @Nullable String global, @Nullable String mine, @NotNull FileMode fileMode, @NotNull String... path) {
    GitProperty[] props = gitProperties;
    for (int i = 0; i < path.length; ++i) {
      final String name = path[i];
      final FileMode mode = i == path.length - 1 ? fileMode : FileMode.TREE;
      props = createForChild(props, name, mode);
    }
    final Map<String, String> text = new HashMap<>();
    for (GitProperty prop : props) {
      prop.apply(text);
    }
    Assert.assertEquals(text.remove("svn:eol-style"), local);
    Assert.assertEquals(text.remove("svn:auto-props"), global);
    Assert.assertEquals(text.remove("svn:mime-type"), mine);
    Assert.assertTrue(text.isEmpty(), text.toString());
  }

  public static class Params {
    @NotNull
    private final GitProperty[] attr;
    @NotNull
    private final String path;
    @NotNull
    private final Map<String, String> expected = new TreeMap<>();

    public Params(@NotNull GitProperty[] attr, @NotNull String path) {
      this.attr = attr;
      this.path = path;
    }

    public Params prop(@NotNull String key, @NotNull String value) {
      expected.put(key, value);
      return this;
    }

    @Override
    public String toString() {
      return path;
    }

    public void check() {
      GitProperty[] gitProperties = createForPath(attr, path);
      final Map<String, String> svnProperties = new HashMap<>();
      for (GitProperty prop : gitProperties) {
        prop.apply(svnProperties);
      }
      for (Map.Entry<String, String> entry : expected.entrySet()) {
        Assert.assertEquals(svnProperties.get(entry.getKey()), entry.getValue(), entry.getKey());
      }
      for (Map.Entry<String, String> entry : svnProperties.entrySet()) {
        Assert.assertEquals(entry.getValue(), expected.get(entry.getKey()), entry.getKey());
      }
    }
  }

  @NotNull
  private static GitProperty[] createForPath(@NotNull GitProperty[] baseProps, @NotNull String path) {
    GitProperty[] props = baseProps;
    String[] pathItems = path.split("/");
    for (int i = 0; i < pathItems.length; ++i) {
      final String name = pathItems[i];
      if (!name.isEmpty()) {
        final FileMode mode = i == pathItems.length - 1 ? FileMode.REGULAR_FILE : FileMode.TREE;
        props = createForChild(props, name, mode);
      }
    }
    return props;
  }

  @NotNull
  private static GitProperty[] createForChild(@NotNull GitProperty[] props, @NotNull String name, @NotNull FileMode mode) {
    GitProperty[] result = new GitProperty[props.length];
    int count = 0;
    for (GitProperty prop : props) {
      final GitProperty child = prop.createForChild(name, mode);
      if (child != null) {
        result[count++] = child;
      }
    }
    return Arrays.copyOf(result, count);
  }
}
