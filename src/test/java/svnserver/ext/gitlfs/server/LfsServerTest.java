/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.server;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * GitLFS server test.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LfsServerTest {
  @Test
  public void checkMimeTypeTest() {
    Assert.assertTrue(LfsServer.checkMimeType("text/html", "text/html"));
    Assert.assertTrue(LfsServer.checkMimeType("text/html; charset=UTF-8", "text/html"));
    Assert.assertTrue(LfsServer.checkMimeType("text/html ; charset=UTF-8", "text/html"));
    Assert.assertFalse(LfsServer.checkMimeType("text/htma", "text/html"));
    Assert.assertFalse(LfsServer.checkMimeType("text/htma; charset=UTF-8", "text/html"));
    Assert.assertFalse(LfsServer.checkMimeType("text/htma ; charset=UTF-8", "text/html"));
  }
}
