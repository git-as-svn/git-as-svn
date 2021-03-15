/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git

import org.eclipse.jgit.lib.Constants
import org.testng.annotations.Test
import svnserver.TestHelper
import svnserver.repository.git.GitCreateMode

/**
 * Test for GitCreateMode.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
class GitCreateModeTest {
    @Test
    fun testEmpty() {
        smoke(GitCreateMode.EMPTY)
    }

    private fun smoke(mode: GitCreateMode) {
        val tempDir = TestHelper.createTempDir("git-as-svn")
        try {
            mode.createRepository(tempDir, setOf(Constants.MASTER)).use { }
        } finally {
            TestHelper.deleteDirectory(tempDir)
        }
    }

    @Test
    fun testExample() {
        smoke(GitCreateMode.EXAMPLE)
    }
}
