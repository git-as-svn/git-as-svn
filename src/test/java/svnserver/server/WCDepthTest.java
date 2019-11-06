/*
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
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;
import svnserver.SvnTestServer;

import java.nio.file.Files;
import java.nio.file.Path;

import static svnserver.SvnTestHelper.sendDeltaAndClose;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class WCDepthTest {
  private SvnTestServer server;
  private SvnOperationFactory factory;
  private Path wc;

  @BeforeMethod
  private void before() throws Exception {
    server = SvnTestServer.createEmpty();
    final SVNRepository repository = server.openSvnRepository();
    factory = server.createOperationFactory();
    wc = Files.createDirectories(server.getTempDirectory().resolve("wc"));

    final ISVNEditor editor = repository.getCommitEditor("", null);
    editor.openRoot(-1);
    editor.addDir("/a", null, -1);
    editor.addDir("/a/b", null, -1);

    editor.addFile("/a/b/e", null, -1);
    editor.changeFileProperty("/a/b/e", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE));
    sendDeltaAndClose(editor, "/a/b/e", null, "e body");

    editor.addDir("/a/b/c", null, -1);

    editor.addFile("/a/b/c/d", null, -1);
    editor.changeFileProperty("/a/b/c/d", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE));
    sendDeltaAndClose(editor, "/a/b/c/d", null, "d body");

    editor.closeDir();
    editor.closeDir();
    editor.closeDir();
    editor.closeDir();
    editor.closeEdit();
  }

  @Test
  public void empty() throws SVNException {
    checkout("", SVNDepth.EMPTY);
    Assert.assertFalse(Files.exists(wc.resolve("a")));

    update("", null);
    Assert.assertFalse(Files.exists(wc.resolve("a")));

    update("", SVNDepth.INFINITY);
    Assert.assertTrue(Files.exists(wc.resolve("a/b/c/d")));
  }

  private void checkout(@NotNull String path, @NotNull SVNDepth depth) throws SVNException {
    final SvnCheckout checkout = factory.createCheckout();
    checkout.setSource(SvnTarget.fromURL(server.getUrl().appendPath(path, true)));
    checkout.setSingleTarget(SvnTarget.fromFile(wc.toFile()));
    checkout.setRevision(SVNRevision.HEAD);
    checkout.setDepth(depth);
    checkout.run();
  }

  private void update(@NotNull String path, @Nullable SVNDepth depth) throws SVNException {
    final SvnUpdate update = factory.createUpdate();
    update.setSingleTarget(SvnTarget.fromFile(wc.resolve(path).toFile()));
    update.setRevision(SVNRevision.HEAD);

    if (depth != null) {
      update.setDepthIsSticky(true);
      update.setDepth(depth);
    }

    update.run();
  }

  @Test
  public void emptySubdir() throws SVNException {
    checkout("a/b", SVNDepth.EMPTY);
    Assert.assertFalse(Files.exists(wc.resolve("c")));
    Assert.assertFalse(Files.exists(wc.resolve("e")));

    update("", null);
    Assert.assertFalse(Files.exists(wc.resolve("c")));
    Assert.assertFalse(Files.exists(wc.resolve("e")));

    update("", SVNDepth.INFINITY);
    Assert.assertTrue(Files.exists(wc.resolve("c/d")));
    Assert.assertTrue(Files.exists(wc.resolve("e")));
  }

  @Test
  public void emptySubdir2() throws SVNException {
    checkout("a/b/c", SVNDepth.EMPTY);
    Assert.assertFalse(Files.exists(wc.resolve("d")));

    update("", null);
    Assert.assertFalse(Files.exists(wc.resolve("d")));

    update("", SVNDepth.INFINITY);
    Assert.assertTrue(Files.exists(wc.resolve("d")));
  }

  @Test
  public void infinity() throws SVNException {
    checkout("", SVNDepth.INFINITY);
    Assert.assertTrue(Files.exists(wc.resolve("a/b/c/d")));
    Assert.assertTrue(Files.exists(wc.resolve("a/b/e")));

    update("", null);
    Assert.assertTrue(Files.exists(wc.resolve("a/b/c/d")));
    Assert.assertTrue(Files.exists(wc.resolve("a/b/e")));
  }

  @Test
  public void infinitySubdir() throws SVNException {
    checkout("a", SVNDepth.INFINITY);
    Assert.assertTrue(Files.exists(wc.resolve("b/c/d")));
    Assert.assertTrue(Files.exists(wc.resolve("b/e")));

    update("", null);
    Assert.assertTrue(Files.exists(wc.resolve("b/c/d")));
    Assert.assertTrue(Files.exists(wc.resolve("b/e")));
  }

  @Test
  public void files() throws SVNException {
    checkout("a/b", SVNDepth.FILES);
    Assert.assertFalse(Files.exists(wc.resolve("c")));
    Assert.assertTrue(Files.exists(wc.resolve("e")));

    update("", null);
    Assert.assertFalse(Files.exists(wc.resolve("c")));
    Assert.assertTrue(Files.exists(wc.resolve("e")));

    update("", SVNDepth.INFINITY);
    Assert.assertTrue(Files.exists(wc.resolve("c/d")));
  }

  @Test
  public void immediates() throws SVNException {
    checkout("a/b", SVNDepth.IMMEDIATES);
    Assert.assertTrue(Files.exists(wc.resolve("c")));
    Assert.assertFalse(Files.exists(wc.resolve("c/d")));
    Assert.assertTrue(Files.exists(wc.resolve("e")));

    update("", null);
    Assert.assertTrue(Files.exists(wc.resolve("c")));
    Assert.assertFalse(Files.exists(wc.resolve("c/d")));
    Assert.assertTrue(Files.exists(wc.resolve("e")));

    update("", SVNDepth.INFINITY);
    Assert.assertTrue(Files.exists(wc.resolve("c/d")));
  }

  @Test
  public void complex() throws SVNException {
    checkout("", SVNDepth.INFINITY);
    Assert.assertTrue(Files.exists(wc.resolve("a/b/c/d")));

    update("a/b", SVNDepth.FILES);
    Assert.assertFalse(Files.exists(wc.resolve("a/b/c")));
    Assert.assertTrue(Files.exists(wc.resolve("a/b/e")));

    update("", SVNDepth.INFINITY);
    Assert.assertTrue(Files.exists(wc.resolve("a/b/c")));
    Assert.assertTrue(Files.exists(wc.resolve("a/b/c/d")));

    update("a/b", SVNDepth.EMPTY);
    Assert.assertFalse(Files.exists(wc.resolve("a/b/c")));
    Assert.assertFalse(Files.exists(wc.resolve("a/b/e")));

    update("", SVNDepth.INFINITY);
    Assert.assertTrue(Files.exists(wc.resolve("a/b/c/d")));
    Assert.assertTrue(Files.exists(wc.resolve("a/b/e")));

    update("a/b", SVNDepth.IMMEDIATES);
    Assert.assertTrue(Files.exists(wc.resolve("a/b/c")));
    Assert.assertTrue(Files.exists(wc.resolve("a/b/e")));
    Assert.assertFalse(Files.exists(wc.resolve("a/b/c/d")));

    update("", SVNDepth.INFINITY);
    Assert.assertTrue(Files.exists(wc.resolve("a/b/c/d")));
  }

  @AfterMethod
  private void after() throws Exception {
    server.close();
  }
}
