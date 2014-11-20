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
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;
import svnserver.TestHelper;
import svnserver.tester.SvnTester;
import svnserver.tester.SvnTesterDataProvider;
import svnserver.tester.SvnTesterExternalListener;
import svnserver.tester.SvnTesterFactory;

import java.io.File;

import static svnserver.SvnTestHelper.sendDeltaAndClose;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
@Listeners(SvnTesterExternalListener.class)
public final class DepthTest {

  @NotNull
  private SvnTester server;
  @NotNull
  private SvnOperationFactory factory;
  @NotNull
  private File wc;

  @NotNull
  private SvnTester create(@NotNull SvnTesterFactory factory) throws Exception {
    final SvnTester tester = factory.create();

    final SVNRepository repo = tester.openSvnRepository();
    final ISVNEditor editor = repo.getCommitEditor("", null);
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

    this.server = tester;
    this.wc = TestHelper.createTempDir("git-as-svn-wc");
    this.factory = new SvnOperationFactory();
    this.factory.setOptions(new DefaultSVNOptions(this.wc, true));
    ISVNAuthenticationManager auth = server.openSvnRepository().getAuthenticationManager();
    if (auth != null) {
      this.factory.setAuthenticationManager(auth);
    }

    return tester;
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

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void empty(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester server = create(factory)) {
      checkout("", SVNDepth.EMPTY);
      Assert.assertFalse(new File(wc, "a").exists());

      update("", null);
      Assert.assertFalse(new File(wc, "a").exists());

      update("", SVNDepth.INFINITY);
      Assert.assertTrue(new File(wc, "a/b/c/d").exists());
    }
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void emptySubdir(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester server = create(factory)) {
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
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void emptySubdir2(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester server = create(factory)) {
      checkout("a/b/c", SVNDepth.EMPTY);
      Assert.assertFalse(new File(wc, "d").exists());

      update("", null);
      Assert.assertFalse(new File(wc, "d").exists());

      update("", SVNDepth.INFINITY);
      Assert.assertTrue(new File(wc, "d").exists());
    }
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void infinity(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester server = create(factory)) {
      checkout("", SVNDepth.INFINITY);
      Assert.assertTrue(new File(wc, "a/b/c/d").exists());
      Assert.assertTrue(new File(wc, "a/b/e").exists());

      update("", null);
      Assert.assertTrue(new File(wc, "a/b/c/d").exists());
      Assert.assertTrue(new File(wc, "a/b/e").exists());
    }
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void infinitySubdir(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester server = create(factory)) {
      checkout("a", SVNDepth.INFINITY);
      Assert.assertTrue(new File(wc, "b/c/d").exists());
      Assert.assertTrue(new File(wc, "b/e").exists());

      update("", null);
      Assert.assertTrue(new File(wc, "b/c/d").exists());
      Assert.assertTrue(new File(wc, "b/e").exists());
    }
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void files(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester server = create(factory)) {
      checkout("a/b", SVNDepth.FILES);
      Assert.assertFalse(new File(wc, "c").exists());
      Assert.assertTrue(new File(wc, "e").exists());

      update("", null);
      Assert.assertFalse(new File(wc, "c").exists());
      Assert.assertTrue(new File(wc, "e").exists());

      update("", SVNDepth.INFINITY);
      Assert.assertTrue(new File(wc, "c/d").exists());
    }
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void immediates(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester server = create(factory)) {
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
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void complex(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester server = create(factory)) {
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
  }
}
