/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import svnserver.TestHelper;

import java.io.File;
import java.io.IOException;

/**
 * Test for GitCreateMode.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitCreateModeTest {
  @Test
  public void testEmpty() throws IOException {
    smoke(GitCreateMode.EMPTY);
  }

  @Test
  public void testExample() throws IOException {
    smoke(GitCreateMode.EXAMPLE);
  }

  private void smoke(@NotNull GitCreateMode mode) throws IOException {
    File tempDir = TestHelper.createTempDir("git-as-svn");
    try {
      mode.createRepository(tempDir, "master");
    } finally {
      TestHelper.deleteDirectory(tempDir);
    }
  }
}
