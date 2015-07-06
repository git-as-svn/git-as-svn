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
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import svnserver.SvnTestServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import static svnserver.SvnTestHelper.*;

/**
 * Check file content filter.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnFilterTest {
  @NotNull
  private final static Map<String, String> propsBinary = new HashMap<String, String>() {{
    put(SVNProperty.MIME_TYPE, SVNFileUtil.BINARY_MIME_TYPE);
  }};

  /**
   * Check file read content on filter change.
   *
   * @throws Exception
   */
  @Test
  public void binaryRead() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      final byte[] uncompressed = "Test file\0".getBytes(StandardCharsets.UTF_8);
      final byte[] compressed = gzip(uncompressed);

      // Add compressed file to repository.
      createFile(repo, "/data.z", compressed, propsBinary);
      createFile(repo, "/data.x", compressed, propsBinary);
      checkFileProp(repo, "/data.z", propsBinary);
      checkFileProp(repo, "/data.x", propsBinary);
      checkFileContent(repo, "/data.z", compressed);
      checkFileContent(repo, "/data.x", compressed);
      // Add filter to file.
      createFile(repo, "/.gitattributes", "*.z\t\t\tfilter=gzip\n", null);
      // On file read now we must have uncompressed content.
      checkFileProp(repo, "/data.z", propsBinary);
      checkFileProp(repo, "/data.x", propsBinary);
      checkFileContent(repo, "/data.z", uncompressed);
      checkFileContent(repo, "/data.x", compressed);
      // Modify filter.
      modifyFile(repo, "/.gitattributes", "*.x\t\t\tfilter=gzip\n", repo.getLatestRevision());
      // Check result.
      checkFileProp(repo, "/data.z", propsBinary);
      checkFileProp(repo, "/data.x", propsBinary);
      checkFileContent(repo, "/data.z", compressed);
      checkFileContent(repo, "/data.x", uncompressed);
    }
  }

  /**
   * Check file read content on filter change.
   *
   * @throws Exception
   */
  @Test
  public void textRead() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      final byte[] uncompressed = "Test file".getBytes(StandardCharsets.UTF_8);
      final byte[] compressed = gzip(uncompressed);

      // Add compressed file to repository.
      createFile(repo, "/data.z", compressed, propsBinary);
      createFile(repo, "/data.x", compressed, propsBinary);
      checkFileProp(repo, "/data.z", propsBinary);
      checkFileProp(repo, "/data.x", propsBinary);
      checkFileContent(repo, "/data.z", compressed);
      checkFileContent(repo, "/data.x", compressed);
      // Add filter to file.
      createFile(repo, "/.gitattributes", "*.z\t\t\tfilter=gzip\n", null);
      // After commit .gitattributes file data.z must change property svn:mime-type and content automagically.
      {
        final Set<String> changed = new HashSet<>();
        repo.log(new String[]{""}, repo.getLatestRevision(), repo.getLatestRevision(), true, false, logEntry -> changed.addAll(logEntry.getChangedPaths().keySet()));
        Assert.assertTrue(changed.contains("/.gitattributes"));
        Assert.assertTrue(changed.contains("/data.z"));
        Assert.assertEquals(changed.size(), 2);
      }
      // On file read now we must have uncompressed content.
      checkFileProp(repo, "/data.z", null);
      checkFileProp(repo, "/data.x", propsBinary);
      checkFileContent(repo, "/data.z", uncompressed);
      checkFileContent(repo, "/data.x", compressed);
      // Modify filter.
      modifyFile(repo, "/.gitattributes", "*.x\t\t\tfilter=gzip\n", repo.getLatestRevision());
      // After commit .gitattributes file data.z must change property svn:mime-type and content automagically.
      {
        final Set<String> changed = new HashSet<>();
        repo.log(new String[]{""}, repo.getLatestRevision(), repo.getLatestRevision(), true, false, logEntry -> changed.addAll(logEntry.getChangedPaths().keySet()));
        Assert.assertTrue(changed.contains("/.gitattributes"));
        Assert.assertTrue(changed.contains("/data.z"));
        Assert.assertTrue(changed.contains("/data.x"));
        Assert.assertEquals(changed.size(), 3);
      }
      // Check result.
      checkFileProp(repo, "/data.z", propsBinary);
      checkFileProp(repo, "/data.x", null);
      checkFileContent(repo, "/data.z", compressed);
      checkFileContent(repo, "/data.x", uncompressed);
    }
  }

  /**
   * Write filtered file.
   *
   * @throws Exception
   */
  @Test()
  public void write() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      final byte[] foo = "Foo file".getBytes(StandardCharsets.UTF_8);
      final byte[] bar = "Test bar".getBytes(StandardCharsets.UTF_8);

      // Add filter to file.
      createFile(repo, "/.gitattributes", "/*.z\t\t\tfilter=gzip\n", null);
      // On file read now we must have uncompressed content.
      createFile(repo, "/data.z", foo, null);
      checkFileContent(repo, "/data.z", foo);
      // Modify file.
      modifyFile(repo, "/data.z", bar, repo.getLatestRevision());
      checkFileContent(repo, "/data.z", bar);
    }
  }

  /**
   * Write file before .gitattributes in single commit.
   *
   * @throws Exception
   */
  @Test()
  public void writeBeforeAttributes() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      final byte[] foo = "Foo file".getBytes(StandardCharsets.UTF_8);
      final byte[] bar = "Test bar".getBytes(StandardCharsets.UTF_8);

      // Create file.
      {
        final ISVNEditor editor = repo.getCommitEditor("Complex commit", null, false, null);
        editor.openRoot(-1);

        editor.addFile("data.z", null, -1);
        sendDeltaAndClose(editor, "data.z", null, foo);

        editor.addFile(".gitattributes", null, -1);
        sendDeltaAndClose(editor, ".gitattributes", null, "*.z\t\t\tfilter=gzip\n");

        editor.closeDir();
        editor.closeEdit();
      }
      // On file read now we must have uncompressed content.
      checkFileContent(repo, "/data.z", foo);

      // Modify file.
      {
        final long rev = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Complex commit", null, false, null);
        editor.openRoot(-1);

        editor.openFile("data.z", rev);
        sendDeltaAndClose(editor, "data.z", foo, bar);

        editor.openFile(".gitattributes", rev);
        sendDeltaAndClose(editor, ".gitattributes", "*.z\t\t\tfilter=gzip\n", "");

        editor.closeDir();
        editor.closeEdit();
      }
      // On file read now we must have uncompressed content.
      checkFileContent(repo, "/data.z", bar);
    }
  }

  /**
   * Write file after .gitattributes in single commit.
   *
   * @throws Exception
   */
  @Test()
  public void writeAfterAttributes() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      final byte[] foo = "Foo file".getBytes(StandardCharsets.UTF_8);
      final byte[] bar = "Test bar".getBytes(StandardCharsets.UTF_8);

      // Create file.
      {
        final ISVNEditor editor = repo.getCommitEditor("Complex commit", null, false, null);
        editor.openRoot(-1);

        editor.addFile(".gitattributes", null, -1);
        sendDeltaAndClose(editor, ".gitattributes", null, "*.z\t\t\tfilter=gzip\n");

        editor.addFile("data.z", null, -1);
        sendDeltaAndClose(editor, "data.z", null, foo);

        editor.closeDir();
        editor.closeEdit();
      }
      // On file read now we must have uncompressed content.
      checkFileContent(repo, "/data.z", foo);

      // Modify file.
      {
        final long rev = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Complex commit", null, false, null);
        editor.openRoot(-1);

        editor.openFile(".gitattributes", rev);
        sendDeltaAndClose(editor, ".gitattributes", "*.z\t\t\tfilter=gzip\n", "");

        editor.openFile("data.z", rev);
        sendDeltaAndClose(editor, "data.z", foo, bar);

        editor.closeDir();
        editor.closeEdit();
      }
      // On file read now we must have uncompressed content.
      checkFileContent(repo, "/data.z", bar);
    }
  }

  /**
   * Copy file with filter change.
   *
   * @throws Exception
   */
  @Test()
  public void copy() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      final byte[] foo = "Foo file".getBytes(StandardCharsets.UTF_8);

      // Add filter to file.
      createFile(repo, "/.gitattributes", "/*.z\t\t\tfilter=gzip\n", null);
      // Create source file.
      createFile(repo, "/data.txt", foo, null);
      // Copy source file with "raw" filter to destination with "gzip" filter.
      {
        final long rev = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Copy file commit", null, false, null);
        editor.openRoot(-1);
        editor.addFile("data.z", "data.txt", rev);
        editor.closeFile("data.z", null);
        editor.closeDir();
        editor.closeEdit();
      }
      // On file read now we must have uncompressed content.
      checkFileContent(repo, "/data.z", foo);
    }
  }

  /**
   * Copy file with filter change.
   *
   * @throws Exception
   */
  @Test()
  public void copyAndModify() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      final byte[] foo = "Foo file".getBytes(StandardCharsets.UTF_8);
      final byte[] bar = "Test bar".getBytes(StandardCharsets.UTF_8);

      // Add filter to file.
      createFile(repo, "/.gitattributes", "/*.z\t\t\tfilter=gzip\n", null);
      // Create source file.
      createFile(repo, "/data.txt", foo, null);
      // Copy source file with "raw" filter to destination with "gzip" filter.
      {
        final long rev = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Copy file commit", null, false, null);
        editor.openRoot(-1);
        editor.addFile("data.z", "data.txt", rev);
        sendDeltaAndClose(editor, "data.z", foo, bar);
        editor.closeDir();
        editor.closeEdit();
      }
      // On file read now we must have uncompressed content.
      checkFileContent(repo, "/data.z", bar);
    }
  }

  private static byte[] gzip(@NotNull byte[] data) throws IOException {
    final ByteArrayOutputStream result = new ByteArrayOutputStream();
    try (OutputStream stream = new GZIPOutputStream(result)) {
      stream.write(data);
    }
    return result.toByteArray();
  }
}
