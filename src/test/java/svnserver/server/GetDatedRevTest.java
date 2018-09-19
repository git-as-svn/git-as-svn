/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnGetStatus;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import svnserver.SvnTestServer;

import java.util.Date;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class GetDatedRevTest {

  @Test
  public void simple() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      Assert.assertEquals(server.openSvnRepository().getDatedRevision(new Date(0)), 0);
    }
  }
}
