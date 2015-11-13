/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlab.mapping;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Test mapping parser.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitLabHookEventTest {
  @Test
  void projectCreated() throws IOException {
    try (Reader reader = new InputStreamReader(getClass().getResourceAsStream("project-created.json"), StandardCharsets.UTF_8)) {
      Assert.assertNotNull(reader);

      final GitLabHookEvent event = GitLabHookEvent.parseEvent(reader);
      Assert.assertNotNull(event);
      Assert.assertEquals(event.getEventName(), "project_create");
      Assert.assertEquals(event.getPathWithNamespace(), "jsmith/storecloud");
      Assert.assertEquals(event.getProjectId(), Integer.valueOf(74));
    }
  }
}
