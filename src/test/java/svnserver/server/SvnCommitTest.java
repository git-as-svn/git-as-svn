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
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import svnserver.SvnTestServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static svnserver.SvnTestHelper.*;

/**
 * Simple update tests.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnCommitTest {
  /**
   * Check file copy.
   * <pre>
   * svn copy README.md@45 README.copy
   * </pre>
   *
   * @throws Exception
   */
  @Test
  public void copyFileFromRevisionTest() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      final String srcFile = "/README.md";
      final String dstFile = "/README.copy";
      final String expectedContent = "New content 2";

      createFile(repo, srcFile, "Old content 1", null);
      modifyFile(repo, srcFile, expectedContent, repo.getLatestRevision());
      final long srcRev = repo.getLatestRevision();
      modifyFile(repo, srcFile, "New content 3", repo.getLatestRevision());

      final ISVNEditor editor = repo.getCommitEditor("Copy file commit", null, false, null);
      editor.openRoot(-1);
      editor.addFile(dstFile, srcFile, srcRev);
      editor.closeFile(dstFile, null);
      editor.closeDir();
      editor.closeEdit();

      // compare content
      checkFileContent(repo, dstFile, expectedContent);
    }
  }

  /**
   * Check file copy.
   * <pre>
   * svn copy README.md@45 README.copy
   * </pre>
   *
   * @throws Exception
   */
  @Test
  public void copyDirFromRevisionTest() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      {
        final ISVNEditor editor = repo.getCommitEditor("Intital state", null, false, null);
        editor.openRoot(-1);
        editor.addDir("/src", null, -1);
        editor.addDir("/src/main", null, -1);
        editor.addFile("/src/main/source.txt", null, -1);
        sendDeltaAndClose(editor, "/src/main/source.txt", null, "Source content");
        editor.closeDir();
        editor.addDir("/src/test", null, -1);
        editor.addFile("/src/test/test.txt", null, -1);
        sendDeltaAndClose(editor, "/src/test/test.txt", null, "Test content");
        editor.closeDir();
        editor.closeDir();
        editor.closeDir();
        editor.closeEdit();
      }
      createFile(repo, "/src/main/copy-a.txt", "A content", null);

      final String srcDir = "/src/main";
      final String dstDir = "/copy";
      final long srcRev = repo.getLatestRevision();

      createFile(repo, "/src/main/copy-b.txt", "B content", null);
      modifyFile(repo, "/src/main/source.txt", "Updated content", repo.getLatestRevision());
      {
        final ISVNEditor editor = repo.getCommitEditor("Copy dir commit", null, false, null);
        editor.openRoot(-1);
        editor.addDir(dstDir, srcDir, srcRev);
        editor.closeDir();
        editor.closeDir();
        editor.closeEdit();
      }

      // compare content
      final Collection<SVNDirEntry> srcList = repo.getDir(srcDir, srcRev, null, 0, new ArrayList());
      final Collection<SVNDirEntry> dstList = repo.getDir(dstDir, repo.getLatestRevision(), null, 0, new ArrayList());
      checkEquals(srcList, dstList);
    }
  }

  private void checkEquals(@NotNull Collection<SVNDirEntry> listA, @NotNull Collection<SVNDirEntry> listB) {
    final Set<String> entries = new HashSet<>();
    for (SVNDirEntry entry : listA) {
      entries.add(entry.getName() + '\t' + entry.getKind() + '\t' + entry.getSize());
    }
    for (SVNDirEntry entry : listB) {
      Assert.assertTrue(entries.remove(entry.getName() + '\t' + entry.getKind() + '\t' + entry.getSize()));
    }
    Assert.assertTrue(entries.isEmpty());
  }

  /**
   * Check commit out-of-date.
   *
   * @throws Exception
   */
  @Test
  public void commitFileOufOfDateTest() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      createFile(repo, "/README.md", "Old content", null);

      final long lastRevision = repo.getLatestRevision();

      modifyFile(repo, "/README.md", "New content 1", lastRevision);
      try {
        modifyFile(repo, "/README.md", "New content 2", lastRevision);
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertEquals(e.getErrorMessage().getErrorCode(), SVNErrorCode.WC_NOT_UP_TO_DATE);
      }
    }
  }

  /**
   * Check commit up-to-date.
   *
   * @throws Exception
   */
  @Test
  public void commitFileUpToDateTest() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      createFile(repo, "/README.md", "Old content 1", null);
      createFile(repo, "/build.gradle", "Old content 2", null);

      final long lastRevision = repo.getLatestRevision();
      modifyFile(repo, "/README.md", "New content 1", lastRevision);
      modifyFile(repo, "/build.gradle", "New content 2", lastRevision);
    }
  }

  /**
   * Check commit without e-mail.
   *
   * @throws Exception
   */
  @Test
  public void commitWithoutEmail() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo1 = server.openSvnRepository();
      createFile(repo1, "/README.md", "Old content 1", null);
      createFile(repo1, "/build.gradle", "Old content 2", null);

      final SVNRepository repo2 = server.openSvnRepository(SvnTestServer.USER_NAME_NO_MAIL, SvnTestServer.PASSWORD);
      final long lastRevision = repo2.getLatestRevision();
      checkFileContent(repo2, "/README.md", "Old content 1");
      try {
        modifyFile(repo2, "/README.md", "New content 1", lastRevision);
        Assert.fail("Users with undefined email can't create commits");
      } catch (SVNAuthenticationException e) {
        Assert.assertTrue(e.getMessage().contains("Users with undefined email can't create commits"));
      }
    }
  }
}
