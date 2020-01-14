/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server;

import com.google.common.base.Strings;
import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import svnserver.SvnTestServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import static svnserver.SvnTestHelper.*;
import static svnserver.server.SvnFilePropertyTest.propsBinary;
import static svnserver.server.SvnFilePropertyTest.propsEolNative;

/**
 * Check file content filter.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class SvnFilterTest {
  @NotNull
  private final static byte[] CONTENT_FOO = (Strings.repeat("Some data\n", 100) + "Foo file\n").getBytes(StandardCharsets.UTF_8);
  @NotNull
  private final static byte[] CONTENT_BAR = (Strings.repeat("Some data\n", 100) + "Bar file\n").getBytes(StandardCharsets.UTF_8);

  /**
   * Check file read content on filter change.
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
      createFile(repo, "/.gitattributes", "*.z\t\t\tfilter=gzip\n", propsEolNative);
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

  private static byte[] gzip(@NotNull byte[] data) throws IOException {
    final ByteArrayOutputStream result = new ByteArrayOutputStream();
    try (OutputStream stream = new GZIPOutputStream(result)) {
      stream.write(data);
    }
    return result.toByteArray();
  }

  /**
   * Check file read content on filter change.
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
      createFile(repo, "/.gitattributes", "*.z\t\t\tfilter=gzip\n", propsEolNative);
      // After commit .gitattributes file data.z must change property svn:mime-type and content automagically.
      {
        final Set<String> changed = new HashSet<>();
        repo.log(new String[]{""}, repo.getLatestRevision(), repo.getLatestRevision(), true, false, logEntry -> changed.addAll(logEntry.getChangedPaths().keySet()));
        Assert.assertTrue(changed.contains("/.gitattributes"));
        Assert.assertTrue(changed.contains("/data.z"));
        Assert.assertEquals(changed.size(), 2);
      }
      // On file read now we must have uncompressed content.
      checkFileProp(repo, "/data.z", propsEolNative);
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
      checkFileProp(repo, "/data.x", propsEolNative);
      checkFileContent(repo, "/data.z", compressed);
      checkFileContent(repo, "/data.x", uncompressed);
    }
  }

  /**
   * Write filtered file.
   */
  @Test()
  public void write() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      // Add filter to file.
      createFile(repo, "/.gitattributes", "/*.z\t\t\tfilter=gzip\n", propsEolNative);
      // On file read now we must have uncompressed content.
      createFile(repo, "/data.z", CONTENT_FOO, propsEolNative);
      checkFileContent(repo, "/data.z", CONTENT_FOO);
      // Modify file.
      modifyFile(repo, "/data.z", CONTENT_BAR, repo.getLatestRevision());
      checkFileContent(repo, "/data.z", CONTENT_BAR);
    }
  }

  /**
   * Write file before .gitattributes in single commit.
   */
  @Test()
  public void writeBeforeAttributes() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      // Create file.
      {
        final ISVNEditor editor = repo.getCommitEditor("Complex commit", null, false, null);
        editor.openRoot(-1);

        editor.addFile("data.z", null, -1);
        editor.changeFileProperty("data.z", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE));
        sendDeltaAndClose(editor, "data.z", null, CONTENT_FOO);

        editor.addFile(".gitattributes", null, -1);
        editor.changeFileProperty(".gitattributes", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE));
        sendDeltaAndClose(editor, ".gitattributes", null, "*.z\t\t\tfilter=gzip\n");

        editor.closeDir();
        editor.closeEdit();
      }
      // On file read now we must have uncompressed content.
      checkFileContent(repo, "/data.z", CONTENT_FOO);

      // Modify file.
      {
        final long rev = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Complex commit", null, false, null);
        editor.openRoot(-1);

        editor.openFile("data.z", rev);
        sendDeltaAndClose(editor, "data.z", CONTENT_FOO, CONTENT_BAR);

        editor.openFile(".gitattributes", rev);
        sendDeltaAndClose(editor, ".gitattributes", "*.z\t\t\tfilter=gzip\n", "");

        editor.closeDir();
        editor.closeEdit();
      }
      // On file read now we must have uncompressed content.
      checkFileContent(repo, "/data.z", CONTENT_BAR);
    }
  }

  /**
   * Write file after .gitattributes in single commit.
   */
  @Test()
  public void writeAfterAttributes() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      // Create file.
      {
        final ISVNEditor editor = repo.getCommitEditor("Complex commit", null, false, null);
        editor.openRoot(-1);

        editor.addFile(".gitattributes", null, -1);
        editor.changeFileProperty(".gitattributes", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE));
        sendDeltaAndClose(editor, ".gitattributes", null, "*.z\t\t\tfilter=gzip\n");

        editor.addFile("data.z", null, -1);
        editor.changeFileProperty("data.z", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE));
        sendDeltaAndClose(editor, "data.z", null, CONTENT_FOO);

        editor.closeDir();
        editor.closeEdit();
      }
      // On file read now we must have uncompressed content.
      checkFileContent(repo, "/data.z", CONTENT_FOO);

      // Modify file.
      {
        final long rev = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Complex commit", null, false, null);
        editor.openRoot(-1);

        editor.openFile(".gitattributes", rev);
        sendDeltaAndClose(editor, ".gitattributes", "*.z\t\t\tfilter=gzip\n", "");

        editor.openFile("data.z", rev);
        sendDeltaAndClose(editor, "data.z", CONTENT_FOO, CONTENT_BAR);

        editor.closeDir();
        editor.closeEdit();
      }
      // On file read now we must have uncompressed content.
      checkFileContent(repo, "/data.z", CONTENT_BAR);
    }
  }

  /**
   * Copy file with filter change.
   */
  @Test()
  public void copy() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      // Add filter to file.
      createFile(repo, "/.gitattributes", "/*.z\t\t\tfilter=gzip\n", propsEolNative);
      // Create source file.
      createFile(repo, "/data.txt", CONTENT_FOO, propsEolNative);
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
      checkFileContent(repo, "/data.z", CONTENT_FOO);
    }
  }

  /**
   * Copy file with filter change.
   */
  @Test()
  public void copyAndModify() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      // Add filter to file.
      createFile(repo, "/.gitattributes", "/*.z\t\t\tfilter=gzip\n", propsEolNative);
      // Create source file.
      createFile(repo, "/data.txt", CONTENT_FOO, propsEolNative);
      // Copy source file with "raw" filter to destination with "gzip" filter.
      {
        final long rev = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Copy file commit", null, false, null);
        editor.openRoot(-1);
        editor.addFile("data.z", "data.txt", rev);
        sendDeltaAndClose(editor, "data.z", CONTENT_FOO, CONTENT_BAR);
        editor.closeDir();
        editor.closeEdit();
      }
      // On file read now we must have uncompressed content.
      checkFileContent(repo, "/data.z", CONTENT_BAR);
    }
  }
}
