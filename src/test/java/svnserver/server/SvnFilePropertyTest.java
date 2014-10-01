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
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import svnserver.SvnTestServer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static svnserver.SvnTestHelper.*;

/**
 * Check file properties.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnFilePropertyTest {
  @NotNull
  private final static Map<String, String> propsEolNative = new HashMap<String, String>() {{
    put(SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_NATIVE);
  }};
  @NotNull
  private final static Map<String, String> propsExecutable = new HashMap<String, String>() {{
    put(SVNProperty.EXECUTABLE, "*");
  }};
  @NotNull
  private final static Map<String, String> propsSymlink = new HashMap<String, String>() {{
    put(SVNProperty.SPECIAL, "*");
  }};
  @NotNull
  private final static Map<String, String> propsAutoProps = new HashMap<String, String>() {{
    put(SVNProperty.INHERITABLE_AUTO_PROPS, "*.txt = svn:eol-style=native\n");
  }};
  @NotNull
  private final static Map<String, String> propsBinary = new HashMap<String, String>() {{
    put(SVNProperty.MIME_TYPE, SVNFileUtil.BINARY_MIME_TYPE);
  }};

  /**
   * Check commit .gitattributes.
   *
   * @throws Exception
   */
  @Test
  public void executable() throws Exception {
    //Map<String, String> props = new HashMap<>()["key":""];
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      createFile(repo, "/non-exec.txt", "", null);
      createFile(repo, "/exec.txt", "", propsExecutable);
      checkFileProp(repo, "/non-exec.txt", null);
      checkFileProp(repo, "/exec.txt", propsExecutable);
      {
        final long latestRevision = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Create directory: /foo", null, false, null);
        editor.openRoot(-1);

        editor.openFile("/non-exec.txt", latestRevision);
        editor.changeFileProperty("/non-exec.txt", SVNProperty.EXECUTABLE, SVNPropertyValue.create("*"));
        editor.closeFile("/non-exec.txt", null);

        editor.openFile("/exec.txt", latestRevision);
        editor.changeFileProperty("/exec.txt", SVNProperty.EXECUTABLE, null);
        editor.closeFile("/exec.txt", null);

        editor.closeDir();
        editor.closeEdit();
      }
      checkFileProp(repo, "/non-exec.txt", propsExecutable);
      checkFileProp(repo, "/exec.txt", null);
    }
  }

  /**
   * Check commit .gitattributes.
   *
   * @throws Exception
   */
  @Test
  public void binary() throws Exception {
    //Map<String, String> props = new HashMap<>()["key":""];
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      createFile(repo, "/data.txt", "Test file", null);
      createFile(repo, "/data.dat", "Test data\0", propsBinary);
      checkFileProp(repo, "/data.txt", null);
      checkFileProp(repo, "/data.dat", propsBinary);
      {
        final long latestRevision = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Modify files", null, false, null);
        editor.openRoot(-1);

        editor.openFile("/data.txt", latestRevision);
        editor.changeFileProperty("/data.txt", SVNProperty.MIME_TYPE, SVNPropertyValue.create(SVNFileUtil.BINARY_MIME_TYPE));
        sendDeltaAndClose(editor, "/data.txt", "Test file", "Test file\0");

        editor.openFile("/data.dat", latestRevision);
        editor.changeFileProperty("/data.dat", SVNProperty.MIME_TYPE, null);
        sendDeltaAndClose(editor, "/data.dat", "Test data\0", "Test data");

        editor.closeDir();
        editor.closeEdit();
      }
      checkFileProp(repo, "/data.txt", propsBinary);
      checkFileProp(repo, "/data.dat", null);
    }
  }

  /**
   * Check commit .gitattributes.
   *
   * @throws Exception
   */
  @Test
  public void symlink() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      final String content = "link foo/bar.txt";
      createFile(repo, "/non-link", content, null);
      createFile(repo, "/link", content, propsSymlink);

      checkFileProp(repo, "/non-link", null);
      checkFileProp(repo, "/link", propsSymlink);

      checkFileContent(repo, "/non-link", content);
      checkFileContent(repo, "/link", content);

      final String content2 = "link bar/foo.txt";
      {
        final long latestRevision = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Change symlink property", null, false, null);
        editor.openRoot(-1);

        editor.openFile("/non-link", latestRevision);
        editor.changeFileProperty("/non-link", SVNProperty.SPECIAL, SVNPropertyValue.create("*"));
        sendDeltaAndClose(editor, "/non-link", content, content2);

        editor.openFile("/link", latestRevision);
        editor.changeFileProperty("/link", SVNProperty.SPECIAL, null);
        sendDeltaAndClose(editor, "/link", content, content2);

        editor.closeDir();
        editor.closeEdit();
      }

      checkFileProp(repo, "/non-link", propsSymlink);
      checkFileProp(repo, "/link", null);

      checkFileContent(repo, "/non-link", content2);
      checkFileContent(repo, "/link", content2);

      {
        final long latestRevision = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Change symlink property", null, false, null);
        editor.openRoot(-1);

        editor.openFile("/non-link", latestRevision);
        editor.changeFileProperty("/non-link", SVNProperty.SPECIAL, null);
        editor.closeFile("/non-link", null);

        editor.openFile("/link", latestRevision);
        editor.changeFileProperty("/link", SVNProperty.SPECIAL, SVNPropertyValue.create("*"));
        editor.closeFile("/link", null);

        editor.closeDir();
        editor.closeEdit();
      }

      checkFileProp(repo, "/non-link", null);
      checkFileProp(repo, "/link", propsSymlink);

      checkFileContent(repo, "/non-link", content2);
      checkFileContent(repo, "/link", content2);
    }
  }

  /**
   * Check commit .gitattributes.
   *
   * @throws Exception
   */
  @Test
  public void commitUpdatePropertiesRoot() throws Exception {
    //Map<String, String> props = new HashMap<>()["key":""];
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      createFile(repo, "/sample.txt", "", null);
      checkFileProp(repo, "/sample.txt", null);
      createFile(repo, "/.gitattributes", "*.txt\t\t\ttext eol=native\n", null);
      // After commit .gitattributes file sample.txt must change property svn:eol-style automagically.
      checkFileProp(repo, "/sample.txt", propsEolNative);
      // After commit .gitattributes directory with .gitattributes must change property svn:auto-props automagically.
      checkDirProp(repo, "/", propsAutoProps);
      // After commit .gitattributes file sample.txt must change property svn:eol-style automagically.
      {
        final Set<String> changed = new HashSet<>();
        repo.log(new String[]{""}, repo.getLatestRevision(), repo.getLatestRevision(), true, false, logEntry -> changed.addAll(logEntry.getChangedPaths().keySet()));
        Assert.assertTrue(changed.contains("/"));
        Assert.assertTrue(changed.contains("/.gitattributes"));
        Assert.assertTrue(changed.contains("/sample.txt"));
        Assert.assertEquals(changed.size(), 3);
      }
    }
  }

  /**
   * Check commit .gitattributes.
   *
   * @throws Exception
   */
  @Test
  public void commitUpdatePropertiesSubdir() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      {
        final ISVNEditor editor = repo.getCommitEditor("Create directory: /foo", null, false, null);
        editor.openRoot(-1);
        editor.addDir("/foo", null, -1);
        // Empty file.
        final String emptyFile = "/foo/.keep";
        editor.addFile(emptyFile, null, -1);
        sendDeltaAndClose(editor, emptyFile, null, "");
        // Close dir
        editor.closeDir();
        editor.closeDir();
        editor.closeEdit();
      }
      createFile(repo, "/sample.txt", "", null);
      createFile(repo, "/foo/sample.txt", "", null);
      checkFileProp(repo, "/sample.txt", null);
      checkFileProp(repo, "/foo/sample.txt", null);

      createFile(repo, "/foo/.gitattributes", "*.txt\t\t\ttext eol=native\n", null);
      // After commit .gitattributes file sample.txt must change property svn:eol-style automagically.
      checkFileProp(repo, "/foo/sample.txt", propsEolNative);
      checkFileProp(repo, "/sample.txt", null);
      // After commit .gitattributes directory with .gitattributes must change property svn:auto-props automagically.
      checkDirProp(repo, "/foo", propsAutoProps);
      // After commit .gitattributes file sample.txt must change property svn:eol-style automagically.
      {
        final Set<String> changed = new HashSet<>();
        repo.log(new String[]{""}, repo.getLatestRevision(), repo.getLatestRevision(), true, false, logEntry -> changed.addAll(logEntry.getChangedPaths().keySet()));
        Assert.assertTrue(changed.contains("/foo"));
        Assert.assertTrue(changed.contains("/foo/.gitattributes"));
        Assert.assertTrue(changed.contains("/foo/sample.txt"));
        Assert.assertEquals(changed.size(), 3);
      }
    }
  }

  /**
   * Check commit .gitattributes.
   *
   * @throws Exception
   */
  @Test
  public void commitDirWithProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      final long latestRevision = repo.getLatestRevision();
      final ISVNEditor editor = repo.getCommitEditor("Create directory: /foo", null, false, null);
      editor.openRoot(-1);
      editor.addDir("/foo", null, latestRevision);
      editor.changeDirProperty(SVNProperty.INHERITABLE_AUTO_PROPS, SVNPropertyValue.create("*.txt = svn:eol-style=native\n"));
      // Empty file.
      final String filePath = "/foo/.gitattributes";
      editor.addFile(filePath, null, -1);
      sendDeltaAndClose(editor, filePath, null, "*.txt\t\t\ttext eol=native\n");
      // Close dir
      editor.closeDir();
      editor.closeDir();
      editor.closeEdit();
    }
  }

  /**
   * Check commit .gitattributes.
   *
   * @throws Exception
   */
  @Test
  public void commitDirWithoutProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      try {
        final long latestRevision = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Create directory: /foo", null, false, null);
        editor.openRoot(-1);
        editor.addDir("/foo", null, latestRevision);
        // Empty file.
        final String filePath = "/foo/.gitattributes";
        editor.addFile(filePath, null, -1);
        sendDeltaAndClose(editor, filePath, null, "*.txt\t\t\ttext eol=native\n");
        // Close dir
        editor.closeDir();
        editor.closeDir();
        editor.closeEdit();
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertTrue(e.getMessage().contains(SVNProperty.INHERITABLE_AUTO_PROPS));
      }
    }
  }

  /**
   * Check commit .gitattributes.
   *
   * @throws Exception
   */
  @Test
  public void commitDirUpdateWithProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      {
        final ISVNEditor editor = repo.getCommitEditor("Create directory: /foo", null, false, null);
        editor.openRoot(-1);
        editor.addDir("/foo", null, -1);
        // Empty file.
        final String filePath = "/foo/.gitattributes";
        editor.addFile(filePath, null, -1);
        sendDeltaAndClose(editor, filePath, null, "");
        // Close dir
        editor.closeDir();
        editor.closeDir();
        editor.closeEdit();
      }
      {
        long latestRevision = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Modify .gitattributes", null, false, null);
        editor.openRoot(-1);
        editor.openDir("/foo", latestRevision);
        editor.changeDirProperty(SVNProperty.INHERITABLE_AUTO_PROPS, SVNPropertyValue.create("*.txt = svn:eol-style=native\n"));
        // Empty file.
        final String filePath = "/foo/.gitattributes";
        editor.openFile(filePath, latestRevision);
        sendDeltaAndClose(editor, filePath, "", "*.txt\t\t\ttext eol=native\n");
        // Close dir
        editor.closeDir();
        editor.closeDir();
        editor.closeEdit();
      }
    }
  }

  /**
   * Check commit .gitattributes.
   *
   * @throws Exception
   */
  @Test
  public void commitDirUpdateWithoutProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();
      {
        final ISVNEditor editor = repo.getCommitEditor("Create directory: /foo", null, false, null);
        editor.openRoot(-1);
        editor.addDir("/foo", null, -1);
        // Empty file.
        final String filePath = "/foo/.gitattributes";
        editor.addFile(filePath, null, -1);
        sendDeltaAndClose(editor, filePath, null, "");
        // Close dir
        editor.closeDir();
        editor.closeDir();
        editor.closeEdit();
      }
      try {
        long latestRevision = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Modify .gitattributes", null, false, null);
        editor.openRoot(-1);
        editor.openDir("/foo", latestRevision);
        // Empty file.
        final String filePath = "/foo/.gitattributes";
        editor.openFile(filePath, latestRevision);
        sendDeltaAndClose(editor, filePath, "", "*.txt\t\t\ttext eol=native\n");
        // Close dir
        editor.closeDir();
        editor.closeDir();
        editor.closeEdit();
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertTrue(e.getMessage().contains(SVNProperty.INHERITABLE_AUTO_PROPS));
      }
    }
  }

  /**
   * Check commit .gitattributes.
   *
   * @throws Exception
   */
  @Test
  public void commitRootWithProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      createFile(repo, "/.gitattributes", "", null);
      {
        long latestRevision = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Modify .gitattributes", null, false, null);
        editor.openRoot(latestRevision);
        editor.changeDirProperty(SVNProperty.INHERITABLE_AUTO_PROPS, SVNPropertyValue.create("*.txt = svn:eol-style=native\n"));
        // Empty file.
        final String filePath = "/.gitattributes";
        editor.openFile(filePath, latestRevision);
        sendDeltaAndClose(editor, filePath, "", "*.txt\t\t\ttext eol=native\n");
        // Close dir
        editor.closeDir();
        editor.closeEdit();
      }
    }
  }

  /**
   * Check commit .gitattributes.
   *
   * @throws Exception
   */
  @Test
  public void commitRootWithoutProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      createFile(repo, "/.gitattributes", "", null);
      try {
        long latestRevision = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Modify .gitattributes", null, false, null);
        editor.openRoot(latestRevision);
        // Empty file.
        final String filePath = "/.gitattributes";
        editor.openFile(filePath, latestRevision);
        sendDeltaAndClose(editor, filePath, "", "*.txt\t\t\ttext eol=native\n");
        // Close dir
        editor.closeDir();
        editor.closeEdit();
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertTrue(e.getMessage().contains(SVNProperty.INHERITABLE_AUTO_PROPS));
      }
    }
  }

  /**
   * Check commit .gitattributes.
   *
   * @throws Exception
   */
  @Test
  public void commitFileWithProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = server.openSvnRepository();

      createFile(repo, "sample.txt", "", null);
      checkFileProp(repo, "/sample.txt", null);

      createFile(repo, ".gitattributes", "*.txt\t\t\ttext eol=native\n", null);
      createFile(repo, "with-props.txt", "", propsEolNative);
      try {
        createFile(repo, "none-props.txt", "", null);
      } catch (SVNException e) {
        Assert.assertTrue(e.getMessage().contains(SVNProperty.EOL_STYLE));
      }
    }
  }
}
