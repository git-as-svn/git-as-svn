/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;
import svnserver.SvnTestServer;

import java.io.File;
import java.io.IOException;

import static svnserver.SvnTestHelper.sendDeltaAndClose;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class WCDepthTest {

  @NotNull
  private SvnTestServer server;
  @NotNull
  private SvnOperationFactory factory;
  @NotNull
  private File wc;

  @BeforeMethod
  private void before() throws Exception {
    server = SvnTestServer.createEmpty();
    final SVNRepository repository = server.openSvnRepository();
    factory = server.createOperationFactory();
    wc = new File(server.getTempDirectory(), "wc");
    Assert.assertTrue(wc.mkdirs());

    final ISVNEditor editor = repository.getCommitEditor("", null);
    editor.openRoot(-1);
    editor.addDir("/a", null, -1);
    editor.addDir("/a/b", null, -1);

    editor.addFile("/a/b/e", null, -1);
    sendDeltaAndClose(editor, "/a/b/e", null, "e body");

    editor.addDir("/a/b/c", null, -1);

    editor.addFile("/a/b/c/d", null, -1);
    sendDeltaAndClose(editor, "/a/b/c/d", null, "d body");

    editor.closeDir();
    editor.closeDir();
    editor.closeDir();
    editor.closeDir();
    editor.closeEdit();
  }

  private void checkout(@NotNull String path, @NotNull SVNDepth depth) throws SVNException {
    final SvnCheckout checkout = factory.createCheckout();
    checkout.setSource(SvnTarget.fromURL(server.getUrl().appendPath(path, true)));
    checkout.setSingleTarget(SvnTarget.fromFile(wc));
    checkout.setRevision(SVNRevision.HEAD);
    checkout.setDepth(depth);
    checkout.run();
  }

  private void update(@NotNull String path, @Nullable SVNDepth depth) throws SVNException {
    final SvnUpdate update = factory.createUpdate();
    update.setSingleTarget(SvnTarget.fromFile(new File(wc, path)));
    update.setRevision(SVNRevision.HEAD);

    if (depth != null) {
      update.setDepthIsSticky(true);
      update.setDepth(depth);
    }

    update.run();
  }

  @Test
  public void empty() throws IOException, SVNException {
    checkout("", SVNDepth.EMPTY);
    Assert.assertFalse(new File(wc, "a").exists());

    update("", null);
    Assert.assertFalse(new File(wc, "a").exists());

    update("", SVNDepth.INFINITY);
    Assert.assertTrue(new File(wc, "a/b/c/d").exists());
  }

  @Test
  public void emptySubdir() throws IOException, SVNException {
    checkout("a/b", SVNDepth.EMPTY);
    Assert.assertFalse(new File(wc, "c").exists());
    Assert.assertFalse(new File(wc, "e").exists());

    update("", null);
    Assert.assertFalse(new File(wc, "c").exists());
    Assert.assertFalse(new File(wc, "e").exists());

    update("", SVNDepth.INFINITY);
    Assert.assertTrue(new File(wc, "c/d").exists());
    Assert.assertTrue(new File(wc, "e").exists());
  }

  @Test
  public void emptySubdir2() throws IOException, SVNException {
    checkout("a/b/c", SVNDepth.EMPTY);
    Assert.assertFalse(new File(wc, "d").exists());

    update("", null);
    Assert.assertFalse(new File(wc, "d").exists());

    update("", SVNDepth.INFINITY);
    Assert.assertTrue(new File(wc, "d").exists());
  }

  @Test
  public void infinity() throws IOException, SVNException {
    checkout("", SVNDepth.INFINITY);
    Assert.assertTrue(new File(wc, "a/b/c/d").exists());
    Assert.assertTrue(new File(wc, "a/b/e").exists());

    update("", null);
    Assert.assertTrue(new File(wc, "a/b/c/d").exists());
    Assert.assertTrue(new File(wc, "a/b/e").exists());
  }

  @Test
  public void infinitySubdir() throws IOException, SVNException {
    checkout("a", SVNDepth.INFINITY);
    Assert.assertTrue(new File(wc, "b/c/d").exists());
    Assert.assertTrue(new File(wc, "b/e").exists());

    update("", null);
    Assert.assertTrue(new File(wc, "b/c/d").exists());
    Assert.assertTrue(new File(wc, "b/e").exists());
  }

  @Test
  public void files() throws IOException, SVNException {
    checkout("a/b", SVNDepth.FILES);
    Assert.assertFalse(new File(wc, "c").exists());
    Assert.assertTrue(new File(wc, "e").exists());

    update("", null);
    Assert.assertFalse(new File(wc, "c").exists());
    Assert.assertTrue(new File(wc, "e").exists());

    update("", SVNDepth.INFINITY);
    Assert.assertTrue(new File(wc, "c/d").exists());
  }

  @Test
  public void immediates() throws IOException, SVNException {
    checkout("a/b", SVNDepth.IMMEDIATES);
    Assert.assertTrue(new File(wc, "c").exists());
    Assert.assertFalse(new File(wc, "c/d").exists());
    Assert.assertTrue(new File(wc, "e").exists());

    update("", null);
    Assert.assertTrue(new File(wc, "c").exists());
    Assert.assertFalse(new File(wc, "c/d").exists());
    Assert.assertTrue(new File(wc, "e").exists());

    update("", SVNDepth.INFINITY);
    Assert.assertTrue(new File(wc, "c/d").exists());
  }

  @Test
  public void complex() throws IOException, SVNException {
    checkout("", SVNDepth.INFINITY);
    Assert.assertTrue(new File(wc, "a/b/c/d").exists());

    update("a/b", SVNDepth.FILES);
    Assert.assertFalse(new File(wc, "a/b/c").exists());
    Assert.assertTrue(new File(wc, "a/b/e").exists());

    update("", SVNDepth.INFINITY);
    Assert.assertTrue(new File(wc, "a/b/c").exists());
    Assert.assertTrue(new File(wc, "a/b/c/d").exists());

    update("a/b", SVNDepth.EMPTY);
    Assert.assertFalse(new File(wc, "a/b/c").exists());
    Assert.assertFalse(new File(wc, "a/b/e").exists());

    update("", SVNDepth.INFINITY);
    Assert.assertTrue(new File(wc, "a/b/c/d").exists());
    Assert.assertTrue(new File(wc, "a/b/e").exists());

    update("a/b", SVNDepth.IMMEDIATES);
    Assert.assertTrue(new File(wc, "a/b/c").exists());
    Assert.assertTrue(new File(wc, "a/b/e").exists());
    Assert.assertFalse(new File(wc, "a/b/c/d").exists());

    update("", SVNDepth.INFINITY);
    Assert.assertTrue(new File(wc, "a/b/c/d").exists());
  }

  @AfterMethod
  private void after() throws Exception {
    server.close();
  }
}
