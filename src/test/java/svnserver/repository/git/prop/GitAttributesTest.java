package svnserver.repository.git.prop;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for GitAttributes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitAttributesTest {
  @Test
  public void testParseAttributes() {
    final GitAttributes attr = new GitAttributes(
        "# Gradle\n" +
            "*.gradle\t\ttext eol=native" +
            "\n" +
            "# Java\n" +
            "\t*.java\t\t\teol=native  text\n" +
            "*.properties  text eol=native\n" +
            " *.py\t\t\ttext eol=native # Python\n" +
            "*.sh\t\t\ttext eol=lf"
    );
    final Map<String, String> props = new HashMap<>();
    attr.modify(props);
    Assert.assertEquals(props.size(), 1);
    Assert.assertEquals(props.get(SVNProperty.INHERITABLE_AUTO_PROPS),
        "*.gradle = svn:eol-style=native\n" +
            "*.java = svn:eol-style=native\n" +
            "*.properties = svn:eol-style=native\n" +
            "*.py = svn:eol-style=native\n" +
            "*.sh = svn:eol-style=lf\n"
    );
  }
}
