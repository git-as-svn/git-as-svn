/*
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
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import svnserver.SvnTestServer;
import svnserver.ext.gitlfs.storage.local.LfsLocalStorageTest;
import svnserver.repository.git.EmptyDirsSupport;
import svnserver.repository.git.GitWriter;

import java.util.*;

import static svnserver.SvnTestHelper.*;
import static svnserver.server.SvnFilePropertyTest.propsEolNative;

/**
 * Simple update tests.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class SvnCommitTest {

  @Test
  public void emptyDirDisabled() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty(EmptyDirsSupport.Disabled)) {
      final SVNRepository repo = server.openSvnRepository();

      final ISVNEditor editor = repo.getCommitEditor("Initial state", null, false, null);
      editor.openRoot(-1);
      editor.addDir("dir", null, -1);
      editor.closeDir();
      editor.closeDir();
      try {
        editor.closeEdit();
        Assert.fail();
      } catch (SVNCancelException e) {
        // Expected
      }
    }
  }

  @Test
  public void createEmptyDir() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty(EmptyDirsSupport.AutoCreateKeepFile)) {
      final SVNRepository repo = server.openSvnRepository();

      final ISVNEditor editor = repo.getCommitEditor("Initial state", null, false, null);
      editor.openRoot(-1);
      editor.addDir("dir", null, -1);
      editor.closeDir();
      editor.closeDir();
      Assert.assertNotNull(editor.closeEdit());

      checkFileContent(repo, "dir/" + GitWriter.keepFileName, GitWriter.keepFileContents);
    }
  }

  @Test
  public void emptyCommit() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      final ISVNEditor editor = repo.getCommitEditor("Initial state", null, false, null);
      editor.openRoot(-1);
      editor.closeDir();
      Assert.assertNotNull(editor.closeEdit());

      Assert.assertEquals(Collections.emptyList(), repo.getDir("", repo.getLatestRevision(), null, 0, new ArrayList<SVNDirEntry>()));
    }
  }

  @Test
  public void removeAllFilesFromDir() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty(EmptyDirsSupport.AutoCreateKeepFile)) {
      final SVNRepository repo = server.openSvnRepository();

      final ISVNEditor editor = repo.getCommitEditor("Initial state", null, false, null);
      editor.openRoot(-1);
      editor.addDir("dir", null, -1);
      editor.addFile("dir/file", null, 0);
      editor.changeFileProperty("dir/file", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE));
      sendDeltaAndClose(editor, "dir/file", null, "text");
      editor.closeDir();
      editor.closeDir();
      Assert.assertNotNull(editor.closeEdit());

      deleteFile(repo, "dir/file");

      checkFileContent(repo, "dir/" + GitWriter.keepFileName, GitWriter.keepFileContents);
    }
  }

  /**
   * Check file copy.
   * <pre>
   * svn copy README.md@45 README.copy
   * </pre>
   */
  @Test
  public void copyFileFromRevisionTest() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      final String srcFile = "/README.md";
      final String dstFile = "/README.copy";
      final String expectedContent = "New content 2";

      createFile(repo, srcFile, "Old content 1", propsEolNative);
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

  @Test
  public void bigFile() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      final byte[] data = LfsLocalStorageTest.bigFile();

      createFile(repo, "bla.bin", data, SvnFilePropertyTest.propsBinary);

      // compare content
      checkFileContent(repo, "bla.bin", data);
    }
  }

  /**
   * Check file copy.
   * <pre>
   * svn copy README.md@45 README.copy
   * </pre>
   */
  @Test
  public void copyDirFromRevisionTest() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      {
        final ISVNEditor editor = repo.getCommitEditor("Initial state", null, false, null);
        editor.openRoot(-1);
        editor.addDir("/src", null, -1);
        editor.addDir("/src/main", null, -1);
        editor.addFile("/src/main/source.txt", null, -1);
        editor.changeFileProperty("/src/main/source.txt", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE));
        sendDeltaAndClose(editor, "/src/main/source.txt", null, "Source content");
        editor.closeDir();
        editor.addDir("/src/test", null, -1);
        editor.addFile("/src/test/test.txt", null, -1);
        editor.changeFileProperty("/src/test/test.txt", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE));
        sendDeltaAndClose(editor, "/src/test/test.txt", null, "Test content");
        editor.closeDir();
        editor.closeDir();
        editor.closeDir();
        editor.closeEdit();
      }
      createFile(repo, "/src/main/copy-a.txt", "A content", propsEolNative);

      final String srcDir = "/src/main";
      final String dstDir = "/copy";
      final long srcRev = repo.getLatestRevision();

      createFile(repo, "/src/main/copy-b.txt", "B content", propsEolNative);
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
      final Collection<SVNDirEntry> srcList = repo.getDir(srcDir, srcRev, null, 0, new ArrayList<SVNDirEntry>());
      final Collection<SVNDirEntry> dstList = repo.getDir(dstDir, repo.getLatestRevision(), null, 0, new ArrayList<SVNDirEntry>());
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
   */
  @Test
  public void commitFileOufOfDateTest() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      createFile(repo, "/README.md", "Old content", propsEolNative);

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
   */
  @Test
  public void commitFileUpToDateTest() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      createFile(repo, "/README.md", "Old content 1", propsEolNative);
      createFile(repo, "/build.gradle", "Old content 2", propsEolNative);

      final long lastRevision = repo.getLatestRevision();
      modifyFile(repo, "/README.md", "New content 1", lastRevision);
      modifyFile(repo, "/build.gradle", "New content 2", lastRevision);
    }
  }

  /**
   * Check commit without e-mail.
   */
  @Test
  public void commitWithoutEmail() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo1 = server.openSvnRepository();
      createFile(repo1, "/README.md", "Old content 1", propsEolNative);
      createFile(repo1, "/build.gradle", "Old content 2", propsEolNative);

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
