/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import svnserver.TestHelper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Test for GitCreateMode.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitCreateModeTest {
  @Test
  public void testEmpty() throws IOException {
    smoke(GitCreateMode.EMPTY);
  }

  private void smoke(@NotNull GitCreateMode mode) throws IOException {
    final Path tempDir = TestHelper.createTempDir("git-as-svn");
    try (Repository repo = mode.createRepository(tempDir, Collections.singleton(Constants.MASTER))) {
      // noop
    } finally {
      TestHelper.deleteDirectory(tempDir);
    }
  }

  @Test
  public void testExample() throws IOException {
    smoke(GitCreateMode.EXAMPLE);
  }
}
