package svnserver.parser;

import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.internal.junit.ArrayAsserts;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
  @Test(timeOut = 60 * 1000)
  public void copyFileFromRevisionTest() throws Exception {
    try (SvnTestServer server = new SvnTestServer("master")) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());

      final long lastRevision = repo.getLatestRevision();
      final String srcFile = "README.md";
      final String dstFile = "README.copy";
      final int srcRev = 45;

      final ISVNEditor editor = repo.getCommitEditor("Copy file commit", null, false, null);
      editor.openRoot(lastRevision);
      editor.addFile(dstFile, srcFile, srcRev);
      editor.closeFile(dstFile, null);
      editor.closeDir();
      final SVNCommitInfo commitInfo = editor.closeEdit();

      // compare content
      final ByteArrayOutputStream srcBuffer = new ByteArrayOutputStream();
      repo.getFile(srcFile, srcRev, null, srcBuffer);

      final ByteArrayOutputStream dstBuffer = new ByteArrayOutputStream();
      repo.getFile(dstFile, commitInfo.getNewRevision(), null, dstBuffer);

      ArrayAsserts.assertArrayEquals(srcBuffer.toByteArray(), dstBuffer.toByteArray());
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
  @Test(timeOut = 60 * 1000)
  public void copyDirFromRevisionTest() throws Exception {
    try (SvnTestServer server = new SvnTestServer("master")) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());

      final long lastRevision = repo.getLatestRevision();
      final String srcDir = "git-as-svn";
      final String dstDir = "git-as-svn.copy";
      final int srcRev = 45;

      final ISVNEditor editor = repo.getCommitEditor("Copy dir commit", null, false, null);
      editor.openRoot(lastRevision);
      editor.addDir(dstDir, srcDir, srcRev);
      editor.closeDir();
      editor.closeDir();

      final SVNCommitInfo commitInfo = editor.closeEdit();

      // compare content
      final Collection<SVNDirEntry> srcList = repo.getDir(srcDir, srcRev, null, 0, new ArrayList());
      final Collection<SVNDirEntry> dstList = repo.getDir(dstDir, commitInfo.getNewRevision(), null, 0, new ArrayList());
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
  @Test(timeOut = 60 * 1000)
  public void commitFileOufOfDateTest() throws Exception {
    try (SvnTestServer server = new SvnTestServer("master")) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());

      final long lastRevision = repo.getLatestRevision();
      modifyFile(repo, "Modify up-to-date commit", "README.md", "New content 1", lastRevision);
      try {
        modifyFile(repo, "Modify out-of-date commit", "README.md", "New content 2", lastRevision);
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
  @Test(timeOut = 60 * 1000)
  public void commitFileUpToDateTest() throws Exception {
    try (SvnTestServer server = new SvnTestServer("master")) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());

      final long lastRevision = repo.getLatestRevision();
      modifyFile(repo, "Modify up-to-date commit", "README.md", "New content 1", lastRevision);
      modifyFile(repo, "Modify up-to-date commit", "build.gradle", "New content 2", lastRevision);
    }
  }

  @NotNull
  private SVNCommitInfo modifyFile(@NotNull SVNRepository repo, @NotNull String logMessage, @NotNull String filePath, @NotNull String newContent, long fileRev) throws SVNException {
    final ByteArrayOutputStream oldData = new ByteArrayOutputStream();
    repo.getFile(filePath, fileRev, null, oldData);

    final ISVNEditor editor = repo.getCommitEditor(logMessage, null, false, null);
    editor.openRoot(fileRev);
    editor.openFile(filePath, fileRev);
    editor.applyTextDelta(filePath, null);
    SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
    String checksum = deltaGenerator.sendDelta(filePath, new ByteArrayInputStream(oldData.toByteArray()), 0, new ByteArrayInputStream(newContent.getBytes(StandardCharsets.UTF_8)), editor, true);
    editor.closeFile(filePath, checksum);
    editor.closeDir();
    return editor.closeEdit();
  }
}
