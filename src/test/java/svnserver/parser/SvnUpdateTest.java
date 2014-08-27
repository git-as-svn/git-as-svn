package svnserver.parser;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * Simple update tests.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnUpdateTest {
  /**
   * Bug: svn up doesnt remove file #18
   * <pre>
   * bozaro@landfill:/tmp/test/git-as-svn$ echo > test.txt
   * bozaro@landfill:/tmp/test/git-as-svn$ svn add test.txt
   * A         test.txt
   * bozaro@landfill:/tmp/test/git-as-svn$ svn commit -m "Add new file"
   * Добавляю          test.txt
   * Передаю данные .
   * Committed revision 58.
   * bozaro@landfill:/tmp/test/git-as-svn$ svn up -r 57
   * Updating '.':
   * В редакции 57.
   * bozaro@landfill:/tmp/test/git-as-svn$ ls -l test.txt
   * -rw-rw-r-- 1 bozaro bozaro 1 авг.  15 00:50 test.txt
   * bozaro@landfill:/tmp/test/git-as-svn$
   * </pre>
   *
   * @throws Exception
   */
  @Test(timeOut = 60 * 1000)
  public void addAndUpdate() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SvnOperationFactory factory = new SvnOperationFactory();
      factory.setAuthenticationManager(server.getAuthenticator());
      final SVNClientManager client = SVNClientManager.newInstance(factory);
      // checkout
      final SvnCheckout checkout = factory.createCheckout();
      checkout.setSource(SvnTarget.fromURL(server.getUrl()));
      checkout.setSingleTarget(SvnTarget.fromFile(server.getTempDirectory()));
      checkout.setRevision(SVNRevision.HEAD);
      final long revision = checkout.run();
      // create file
      File newFile = new File(server.getTempDirectory(), "somefile.txt");
      TestHelper.saveFile(newFile, "Bla Bla Bla");
      // add file
      client.getWCClient().doAdd(newFile, false, false, false, SVNDepth.INFINITY, false, true);
      // commit new file
      client.getCommitClient().doCommit(new File[]{newFile}, false, "Add file commit", null, null, false, false, SVNDepth.INFINITY);
      // update for checkout revision
      client.getUpdateClient().doUpdate(server.getTempDirectory(), SVNRevision.create(revision), SVNDepth.INFINITY, false, false);
      // file must be remove
      Assert.assertFalse(newFile.exists());
    }
  }
}
