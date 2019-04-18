/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server;

import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.SVNRepository;
import svnserver.tester.SvnTester;
import svnserver.tester.SvnTesterDataProvider;
import svnserver.tester.SvnTesterExternalListener;
import svnserver.tester.SvnTesterFactory;

import java.util.ArrayList;
import java.util.List;

import static svnserver.SvnTestHelper.createFile;
import static svnserver.SvnTestHelper.modifyFile;
import static svnserver.server.CheckPathAndStatCmdTest.propsEolNative;

@Listeners(SvnTesterExternalListener.class)
public final class GetFileRevsTest {

  @NotNull
  private static final String fileName = "/file.txt";

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void simple(@NotNull SvnTesterFactory factory) throws Exception {
    if (System.getenv("TRAVIS") != null && factory.toString().equals("Native"))
      throw new SkipException("Travis has very old svn that doesn't pass half of these tests");

    try (SvnTester tester = factory.create()) {
      final SVNRepository repository = tester.openSvnRepository();

      createFile(repository, fileName, "a\nb\nc\n", propsEolNative);
      modifyFile(repository, fileName, "a\nd\nc\n", repository.getLatestRevision());

      final long latestRevision = repository.getLatestRevision();

      try {
        assertFileRevisions(repository, 0, 0);
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertEquals(e.getErrorMessage().getErrorCode(), SVNErrorCode.FS_NOT_FILE);
      }

      if (hasCapability(repository, SVNCapability.GET_FILE_REVS_REVERSED))
        assertFileRevisions(repository, -1, 0, latestRevision, latestRevision - 1);

      assertFileRevisions(repository, -1, -1, latestRevision);
      assertFileRevisions(repository, 0, -1, latestRevision - 1, latestRevision);
      assertFileRevisions(repository, latestRevision - 1, latestRevision - 1, latestRevision - 1);
    }
  }

  private void assertFileRevisions(@NotNull SVNRepository repository, long startRev, long endRev, long... expected) throws SVNException {
    final List<SVNFileRevision> fileRevisions = new ArrayList<>();

    repository.getFileRevisions(fileName, fileRevisions, startRev, endRev);

    Assert.assertEquals(fileRevisions.size(), expected.length);
    for (int i = 0; i < expected.length; ++i) {
      Assert.assertEquals(fileRevisions.get(i).getRevision(), expected[i]);
    }
  }

  private static boolean hasCapability(@NotNull SVNRepository repository, @NotNull SVNCapability capability) throws SVNException {
    try {
      return repository.hasCapability(capability);
    } catch (SVNException e) {
      if (e.getErrorMessage().getErrorCode() != SVNErrorCode.UNKNOWN_CAPABILITY)
        throw e;
    }
    return false;
  }
}
