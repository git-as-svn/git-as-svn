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
      checkout.setSingleTarget(SvnTarget.fromFile(server.getTempDirectory()));
      checkout.setRevision(SVNRevision.create(1));
      checkout.run();

      final SvnGetStatus status = factory.createGetStatus();
      status.setSingleTarget(SvnTarget.fromFile(server.getTempDirectory()));
      status.setRevision(SVNRevision.create(2));
      status.run();
    }
  }
}
