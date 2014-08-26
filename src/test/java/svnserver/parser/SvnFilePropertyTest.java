package svnserver.parser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Check file properties.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnFilePropertyTest {
  private final static byte[] emptyBytes = {};

  /**
   * Check commit .gitattributes.
   *
   * @throws Exception
   */
  @Test(timeOut = 60 * 1000)
  public void commitUpdatePropertiesRoot() throws Exception {
    //Map<String, String> props = new HashMap<>()["key":""];
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());

      createFile(repo, "/sample.txt", "", null);
      {
        SVNProperties props = new SVNProperties();
        repo.getFile("/sample.txt", repo.getLatestRevision(), props, null);
        Assert.assertEquals(props.getStringValue(SVNProperty.EOL_STYLE), null);
      }
      createFile(repo, "/.gitattributes", "*.txt\t\t\ttext eol=native\n", null);
      // After commit .gitattributes file sample.txt must change property svn:eol-style automagically.
      {
        SVNProperties props = new SVNProperties();
        repo.getFile("/sample.txt", repo.getLatestRevision(), props, null);
        Assert.assertEquals(props.getStringValue(SVNProperty.EOL_STYLE), SVNProperty.EOL_STYLE_NATIVE);
      }
      // After commit .gitattributes directory with .gitattributes must change property svn:auto-props automagically.
      {
        SVNProperties props = new SVNProperties();
        repo.getDir("/", repo.getLatestRevision(), props, new ArrayList<>());
        Assert.assertEquals(props.getStringValue(SVNProperty.INHERITABLE_AUTO_PROPS), "*.txt = svn:eol-style=native\n");
      }
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
  @Test(timeOut = 60 * 1000)
  public void commitUpdatePropertiesSubdir() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());
      {
        final ISVNEditor editor = repo.getCommitEditor("Create directory: /foo", null, false, null);
        editor.openRoot(-1);
        editor.addDir("/foo", null, -1);
        // Empty file.
        final String emptyFile = "/foo/.keep";
        editor.addFile(emptyFile, null, -1);
        sendDeltaAndClose(editor, emptyFile, null, null);
        // Close dir
        editor.closeDir();
        editor.closeDir();
        editor.closeEdit();
      }
      createFile(repo, "/sample.txt", "", null);
      createFile(repo, "/foo/sample.txt", "", null);
      {
        SVNProperties props = new SVNProperties();
        repo.getFile("/sample.txt", repo.getLatestRevision(), props, null);
        Assert.assertEquals(props.getStringValue(SVNProperty.EOL_STYLE), null);
      }
      {
        SVNProperties props = new SVNProperties();
        repo.getFile("/foo/sample.txt", repo.getLatestRevision(), props, null);
        Assert.assertEquals(props.getStringValue(SVNProperty.EOL_STYLE), null);
      }
      createFile(repo, "/foo/.gitattributes", "*.txt\t\t\ttext eol=native\n", null);
      // After commit .gitattributes file sample.txt must change property svn:eol-style automagically.
      {
        SVNProperties props = new SVNProperties();
        repo.getFile("/foo/sample.txt", repo.getLatestRevision(), props, null);
        Assert.assertEquals(props.getStringValue(SVNProperty.EOL_STYLE), SVNProperty.EOL_STYLE_NATIVE);
      }
      {
        SVNProperties props = new SVNProperties();
        repo.getFile("/sample.txt", repo.getLatestRevision(), props, null);
        Assert.assertEquals(props.getStringValue(SVNProperty.EOL_STYLE), null);
      }
      // After commit .gitattributes directory with .gitattributes must change property svn:auto-props automagically.
      {
        SVNProperties props = new SVNProperties();
        repo.getDir("/foo", repo.getLatestRevision(), props, new ArrayList<>());
        Assert.assertEquals(props.getStringValue(SVNProperty.INHERITABLE_AUTO_PROPS), "*.txt = svn:eol-style=native\n");
      }
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
  @Test(timeOut = 60 * 1000)
  public void commitDirWithProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());

      final long latestRevision = repo.getLatestRevision();
      final ISVNEditor editor = repo.getCommitEditor("Create directory: /foo", null, false, null);
      editor.openRoot(-1);
      editor.addDir("/foo", null, latestRevision);
      editor.changeDirProperty(SVNProperty.INHERITABLE_AUTO_PROPS, SVNPropertyValue.create("*.txt = svn:eol-style=native\n"));
      // Empty file.
      final String filePath = "/foo/.gitattributes";
      editor.addFile(filePath, null, -1);
      sendDeltaAndClose(editor, filePath, null, "*.txt\t\t\ttext eol=native\n".getBytes(StandardCharsets.UTF_8));
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
  @Test(timeOut = 60 * 1000)
  public void commitDirWithoutProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());
      try {
        final long latestRevision = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Create directory: /foo", null, false, null);
        editor.openRoot(-1);
        editor.addDir("/foo", null, latestRevision);
        // Empty file.
        final String filePath = "/foo/.gitattributes";
        editor.addFile(filePath, null, -1);
        sendDeltaAndClose(editor, filePath, null, "*.txt\t\t\ttext eol=native\n".getBytes(StandardCharsets.UTF_8));
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
  @Test(timeOut = 60 * 1000)
  public void commitDirUpdateWithProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());
      {
        final ISVNEditor editor = repo.getCommitEditor("Create directory: /foo", null, false, null);
        editor.openRoot(-1);
        editor.addDir("/foo", null, -1);
        // Empty file.
        final String filePath = "/foo/.gitattributes";
        editor.addFile(filePath, null, -1);
        sendDeltaAndClose(editor, filePath, null, emptyBytes);
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
        sendDeltaAndClose(editor, filePath, emptyBytes, "*.txt\t\t\ttext eol=native\n".getBytes(StandardCharsets.UTF_8));
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
  @Test(timeOut = 60 * 1000)
  public void commitDirUpdateWithoutProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());
      {
        final ISVNEditor editor = repo.getCommitEditor("Create directory: /foo", null, false, null);
        editor.openRoot(-1);
        editor.addDir("/foo", null, -1);
        // Empty file.
        final String filePath = "/foo/.gitattributes";
        editor.addFile(filePath, null, -1);
        sendDeltaAndClose(editor, filePath, null, emptyBytes);
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
        sendDeltaAndClose(editor, filePath, emptyBytes, "*.txt\t\t\ttext eol=native\n".getBytes(StandardCharsets.UTF_8));
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
  @Test(timeOut = 60 * 1000)
  public void commitRootWithProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());

      createFile(repo, "/.gitattributes", "", null);
      {
        long latestRevision = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Modify .gitattributes", null, false, null);
        editor.openRoot(latestRevision);
        editor.changeDirProperty(SVNProperty.INHERITABLE_AUTO_PROPS, SVNPropertyValue.create("*.txt = svn:eol-style=native\n"));
        // Empty file.
        final String filePath = "/.gitattributes";
        editor.openFile(filePath, latestRevision);
        sendDeltaAndClose(editor, filePath, emptyBytes, "*.txt\t\t\ttext eol=native\n".getBytes(StandardCharsets.UTF_8));
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
  @Test(timeOut = 60 * 1000)
  public void commitRootWithoutProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());

      createFile(repo, "/.gitattributes", "", null);
      try {
        long latestRevision = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Modify .gitattributes", null, false, null);
        editor.openRoot(latestRevision);
        // Empty file.
        final String filePath = "/.gitattributes";
        editor.openFile(filePath, latestRevision);
        sendDeltaAndClose(editor, filePath, emptyBytes, "*.txt\t\t\ttext eol=native\n".getBytes(StandardCharsets.UTF_8));
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
  @Test(timeOut = 60 * 1000)
  public void commitFileWithProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());

      createFile(repo, "sample.txt", "", null);
      {
        SVNProperties props = new SVNProperties();
        repo.getFile("/sample.txt", repo.getLatestRevision(), props, null);
        Assert.assertEquals(props.getStringValue(SVNProperty.EOL_STYLE), null);
      }
      createFile(repo, ".gitattributes", "*.txt\t\t\ttext eol=native\n", null);
      createFile(repo, "with-props.txt", "", SVNProperty.EOL_STYLE_NATIVE);
      try {
        createFile(repo, "none-props.txt", "", null);
      } catch (SVNException e) {
        Assert.assertTrue(e.getMessage().contains(SVNProperty.EOL_STYLE));
      }
    }
  }

  @NotNull
  private SVNCommitInfo createFile(@NotNull SVNRepository repo, @NotNull String filePath, @NotNull String content, @Nullable String eolStyle) throws SVNException, IOException {
    final ISVNEditor editor = repo.getCommitEditor("Create file: " + filePath, null, false, null);
    editor.openRoot(-1);
    int index = 0;
    int depth = 0;
    while (true) {
      index = filePath.indexOf('/', index + 1);
      if (index < 0) {
        break;
      }
      editor.openDir(filePath.substring(0, index), -1);
      depth++;
    }
    editor.addFile(filePath, null, -1);
    if (eolStyle != null) {
      editor.changeFileProperty(filePath, SVNProperty.EOL_STYLE, SVNPropertyValue.create(eolStyle));
    }
    sendDeltaAndClose(editor, filePath, null, content.getBytes(StandardCharsets.UTF_8));
    for (int i = 0; i < depth; ++i) {
      editor.closeDir();
    }
    editor.closeDir();
    return editor.closeEdit();
  }

  private void sendDeltaAndClose(@NotNull ISVNEditor editor, @NotNull String filePath, @Nullable byte[] oldData, @Nullable byte[] newData) throws SVNException, IOException {
    try (
        ByteArrayInputStream oldStream = new ByteArrayInputStream(oldData == null ? emptyBytes : oldData);
        ByteArrayInputStream newStream = new ByteArrayInputStream(newData == null ? emptyBytes : newData)
    ) {
      editor.applyTextDelta(filePath, null);
      SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
      String checksum = deltaGenerator.sendDelta(filePath, oldStream, 0, newStream, editor, true);
      editor.closeFile(filePath, checksum);
    }
  }
}
