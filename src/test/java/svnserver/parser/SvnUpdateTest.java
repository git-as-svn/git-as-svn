package svnserver.parser;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.internal.junit.ArrayAsserts;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCat;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * Simple update tests.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnUpdateTest {
  @Test(timeOut = 60 * 1000)
  public void copyTest() throws Exception {
    try (SvnTestServer server = new SvnTestServer("master")) {
      final SvnOperationFactory factory = new SvnOperationFactory();
      factory.setAuthenticationManager(server.getAuthenticator());
      final SVNClientManager client = SVNClientManager.newInstance(factory);
      // checkout
      final SvnCheckout checkout = factory.createCheckout();
      checkout.setSource(SvnTarget.fromURL(server.getUrl()));
      checkout.setSingleTarget(SvnTarget.fromFile(server.getTempDirectory()));
      checkout.setRevision(SVNRevision.HEAD);
      checkout.run();
      // copy file (rev 45 - bed28fa0793378b98912063bca5ad44e4c049df8)
      final SVNRevision srcRev = SVNRevision.create(45);
      final File srcFile = new File(server.getTempDirectory(), "README.md");
      final File dstFile = new File(server.getTempDirectory(), "README.copy");
      client.getCopyClient().doCopy(new SVNCopySource[]{
          new SVNCopySource(srcRev, srcRev, srcFile)
      }, dstFile, false, false, true);
      // commit new file
      final SVNCommitInfo commitInfo = client.getCommitClient().doCommit(new File[]{dstFile}, false, "Add file commit", null, null, false, false, SVNDepth.INFINITY);
      // cat source file
      final ByteArrayOutputStream srcBuffer = new ByteArrayOutputStream();
      final SvnCat srcCat = factory.createCat();
      srcCat.setRevision(srcRev);
      srcCat.setSingleTarget(SvnTarget.fromFile(srcFile, srcRev));
      srcCat.setOutput(srcBuffer);
      srcCat.run();
      // cat destination file
      final ByteArrayOutputStream dstBuffer = new ByteArrayOutputStream();
      final SvnCat dstCat = factory.createCat();
      dstCat.setRevision(srcRev);
      dstCat.setSingleTarget(SvnTarget.fromFile(dstFile, SVNRevision.create(commitInfo.getNewRevision())));
      dstCat.setOutput(dstBuffer);
      dstCat.run();
      // compare result
      ArrayAsserts.assertArrayEquals(srcBuffer.toByteArray(), dstBuffer.toByteArray());
    }
  }

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
    try (SvnTestServer server = new SvnTestServer("master")) {
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
      TestHelper.createFile(newFile, "Bla Bla Bla");
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
