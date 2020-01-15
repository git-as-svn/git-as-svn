/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop;

import org.eclipse.jgit.lib.FileMode;
import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import svnserver.TestHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tests for GitAttributes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitEolTest {
  @DataProvider(name = "parseAttributesData")
  public static Object[][] parseAttributesData() throws IOException {
    final GitProperty[] attr;
    try (InputStream in = TestHelper.asStream(
        "# comment\n" +
            "*     text\n" +
            "*.txt text\n" +
            // Tests that eol attribute works even without text attribute
            "*.md  eol=lf\n" +
            "*.dat -text\n" +
            "3.md -text\n" +
            "*.bin binary\n" +
            "1.bin text\n" +
            "2.bin text\n"
    )) {
      attr = new GitAttributesFactory().create(in);
    }

    final Params[] params = new Params[]{
        new Params(attr, "/").prop(SVNProperty.INHERITABLE_AUTO_PROPS, "*.txt = svn:eol-style=native\n" +
            "*.md = svn:eol-style=LF\n" +
            "*.dat = svn:mime-type=application/octet-stream\n" +
            "3.md = svn:mime-type=application/octet-stream\n" +
            "*.bin = svn:mime-type=application/octet-stream\n" +
            "1.bin = svn:eol-style=native\n" +
            "2.bin = svn:eol-style=native\n"),
        new Params(attr, "README.md").prop(SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_LF),
        new Params(attr, "foo.dat").prop(SVNProperty.MIME_TYPE, SVNFileUtil.BINARY_MIME_TYPE),
        new Params(attr, "foo.txt").prop(SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_NATIVE),
        new Params(attr, "foo.bin").prop(SVNProperty.MIME_TYPE, SVNFileUtil.BINARY_MIME_TYPE),

        new Params(attr, "1.bin").prop(SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_NATIVE),
        new Params(attr, "2.bin").prop(SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_NATIVE),
        new Params(attr, "3.md").prop(SVNProperty.MIME_TYPE, SVNFileUtil.BINARY_MIME_TYPE),

        new Params(attr, "changelog").prop(SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_NATIVE),
    };
    final Object[][] result = new Object[params.length][];
    for (int i = 0; i < params.length; ++i) {
      result[i] = new Object[]{params[i]};
    }
    return result;
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

  @Test(dataProvider = "parseAttributesData")
  public void parseAttributes(@NotNull Params params) {
    params.check();
  }

  public static class Params {
    @NotNull
    private final GitProperty[] attr;
    @NotNull
    private final String path;
    @NotNull
    private final Map<String, String> expected = new TreeMap<>();

    Params(@NotNull GitProperty[] attr, @NotNull String path) {
      this.attr = attr;
      this.path = path;
    }

    Params prop(@NotNull String key, @NotNull String value) {
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
}
