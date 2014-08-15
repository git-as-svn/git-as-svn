package svnserver.parser;

import org.testng.annotations.Test;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

/**
 * Simple checkout tests.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnCheckoutTest {
  @Test(timeOut = 60 * 1000)
  public void checkoutRootRevision() throws Exception {
    try (SvnTestServer server = new SvnTestServer("master")) {
      final SvnOperationFactory factory = new SvnOperationFactory();
      factory.setAuthenticationManager(server.getAuthenticator());
      final SvnCheckout checkout = factory.createCheckout();
      checkout.setSource(SvnTarget.fromURL(server.getUrl()));
      checkout.setSingleTarget(SvnTarget.fromFile(server.getTempDirectory()));
      checkout.setRevision(SVNRevision.create(0));
      checkout.run();
    }
  }
}
