/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.SVNRepository;
import svnserver.tester.SvnTester;
import svnserver.tester.SvnTesterDataProvider;
import svnserver.tester.SvnTesterExternalListener;
import svnserver.tester.SvnTesterFactory;

import java.util.Map;

import static svnserver.SvnTestHelper.createFile;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
@Listeners(SvnTesterExternalListener.class)
public final class CheckPathAndStatCmdTest {
  @NotNull
  final static Map<String, String> propsEolNative = ImmutableMap.<String, String>builder()
      .put(SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_NATIVE)
      .build();

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void nonexistentRev(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester tester = create(factory)) {
      final SVNRepository repository = tester.openSvnRepository();

      final long revision = repository.getLatestRevision() + 1;
      try {
        repository.checkPath("", revision);
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertEquals(e.getErrorMessage().getErrorCode(), SVNErrorCode.FS_NO_SUCH_REVISION);
      }

      try {
        repository.info("", revision);
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertEquals(e.getErrorMessage().getErrorCode(), SVNErrorCode.FS_NO_SUCH_REVISION);
      }
    }
  }

  @NotNull
  private SvnTester create(@NotNull SvnTesterFactory factory) throws Exception {
    SvnTester tester = factory.create();
    createFile(tester.openSvnRepository(), "/existent", "", propsEolNative);
    return tester;
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void nonexistentFileInInitialRev(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester tester = create(factory)) {
      final SVNRepository repository = tester.openSvnRepository();
      assertPath(repository, "/existent", 0, SVNNodeKind.NONE);
    }
  }

  private static void assertPath(@NotNull SVNRepository repository, @NotNull String path, long rev, @NotNull SVNNodeKind expectedKind) throws SVNException {
    final SVNNodeKind nodeKind = repository.checkPath(path, rev);
    Assert.assertEquals(nodeKind, expectedKind);

    final SVNDirEntry info = repository.info(path, rev);
    if (expectedKind == SVNNodeKind.NONE) {
      Assert.assertNull(info);
    } else {
      Assert.assertEquals(info.getKind(), expectedKind);
      Assert.assertEquals(info.getRevision(), rev);
    }
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void nonexistentFile(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester tester = create(factory)) {
      final SVNRepository repository = tester.openSvnRepository();
      assertPath(repository, "/nonexistent", repository.getLatestRevision(), SVNNodeKind.NONE);
    }
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void root(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester tester = create(factory)) {
      final SVNRepository repository = tester.openSvnRepository();
      assertPath(repository, "", repository.getLatestRevision(), SVNNodeKind.DIR);
    }
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void existentFile(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester tester = create(factory)) {
      final SVNRepository repository = tester.openSvnRepository();
      assertPath(repository, "/existent", repository.getLatestRevision(), SVNNodeKind.FILE);
    }
  }
}
