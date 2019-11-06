/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server;

import org.testng.annotations.Test;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnGetStatus;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import svnserver.SvnTestServer;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class StatusCmdTest {

  @Test
  public void simple() throws Exception {
    try (SvnTestServer server = SvnTestServer.createMasterRepository()) {
      final SvnOperationFactory factory = server.createOperationFactory();

      final SvnCheckout checkout = factory.createCheckout();
      checkout.setSource(SvnTarget.fromURL(server.getUrl()));
      checkout.setSingleTarget(SvnTarget.fromFile(server.getTempDirectory().toFile()));
      checkout.setRevision(SVNRevision.create(1));
      checkout.run();

      final SvnGetStatus status = factory.createGetStatus();
      status.setSingleTarget(SvnTarget.fromFile(server.getTempDirectory().toFile()));
      status.setRevision(SVNRevision.create(2));
      status.run();
    }
  }
}
