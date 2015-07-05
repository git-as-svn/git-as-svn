/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server;

import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import svnserver.SvnTestServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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
  public void simpleBinaryRead() throws Exception {
    //Map<String, String> props = new HashMap<>()["key":""];
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
  public void simpleTextRead() throws Exception {
    //Map<String, String> props = new HashMap<>()["key":""];
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
      // On file read now we must have uncompressed content.
      checkFileProp(repo, "/data.z", null);
      checkFileProp(repo, "/data.x", propsBinary);
      checkFileContent(repo, "/data.z", uncompressed);
      checkFileContent(repo, "/data.x", compressed);
      // Modify filter.
      modifyFile(repo, "/.gitattributes", "*.x\t\t\tfilter=gzip\n", repo.getLatestRevision());
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
  public void simpleWrite() throws Exception {
    //Map<String, String> props = new HashMap<>()["key":""];
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

  private static byte[] gzip(@NotNull byte[] data) throws IOException {
    final ByteArrayOutputStream result = new ByteArrayOutputStream();
    try (OutputStream stream = new GZIPOutputStream(result)) {
      stream.write(data);
    }
    return result.toByteArray();
  }
}
