/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git.prop;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests for GitAttributes.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitTortoiseTest {
  @Test
  public void testParseAttributes() throws IOException {
    final GitProperty attr = new GitTortoise(
        "[bugtraq]\n" +
            "\turl = http://bugtracking/browse/%BUGID%\n" +
            "\tlogregex = (BUG-\\\\d+)\n" +
            "\twarnifnoissue = false"
    );
    final Map<String, String> props = new HashMap<>();
    attr.apply(props);
    Assert.assertEquals(props.size(), 3);
    Assert.assertEquals(props.get("bugtraq:url"), "http://bugtracking/browse/%BUGID%");
    Assert.assertEquals(props.get("bugtraq:logregex"), "(BUG-\\d+)");
    Assert.assertEquals(props.get("bugtraq:warnifnoissue"), "false");
  }
}
