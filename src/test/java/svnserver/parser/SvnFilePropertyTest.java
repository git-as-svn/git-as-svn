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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Check file properties.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class SvnFilePropertyTest {
  @NotNull
  private final static byte[] emptyBytes = {};
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

  /**
   * Check commit .gitattributes.
   *
   * @throws Exception
   */
  @Test(timeOut = 60 * 1000)
  public void executable() throws Exception {
    //Map<String, String> props = new HashMap<>()["key":""];
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());

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
  @Test(timeOut = 60 * 1000)
  public void symlink() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());

      final String content = "link foo/bar.txt";
      createFile(repo, "/non-link", content, null);
      createFile(repo, "/link", content, propsSymlink);

      checkFileProp(repo, "/non-link", null);
      checkFileProp(repo, "/link", propsSymlink);

      checkFileContent(repo, "/non-link", content);
      checkFileContent(repo, "/link", content);

      {
        final long latestRevision = repo.getLatestRevision();
        final ISVNEditor editor = repo.getCommitEditor("Change symlink property", null, false, null);
        editor.openRoot(-1);

        editor.openFile("/non-link", latestRevision);
        editor.changeFileProperty("/non-link", SVNProperty.SPECIAL, SVNPropertyValue.create("*"));
        sendDeltaAndClose(editor, "/non-link", content, content);

        editor.openFile("/link", latestRevision);
        editor.changeFileProperty("/link", SVNProperty.SPECIAL, null);
        sendDeltaAndClose(editor, "/link", content, content);

        editor.closeDir();
        editor.closeEdit();
      }

      checkFileProp(repo, "/non-link", propsSymlink);
      checkFileProp(repo, "/link", null);

      checkFileContent(repo, "/non-link", content);
      checkFileContent(repo, "/link", content);

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

      checkFileContent(repo, "/non-link", content);
      checkFileContent(repo, "/link", content);
    }
  }

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
  @Test(timeOut = 60 * 1000)
  public void commitFileWithProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());

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

  private void checkFileProp(@NotNull SVNRepository repo, @NotNull String filePath, @Nullable Map<String, String> expected) throws SVNException {
    SVNProperties props = new SVNProperties();
    repo.getFile(filePath, repo.getLatestRevision(), props, null);
    checkProp(props, expected);
  }

  private void checkDirProp(@NotNull SVNRepository repo, @NotNull String filePath, @Nullable Map<String, String> expected) throws SVNException {
    SVNProperties props = new SVNProperties();
    repo.getDir(filePath, repo.getLatestRevision(), props, new ArrayList<>());
    checkProp(props, expected);
  }

  private void checkProp(@NotNull SVNProperties props, @Nullable Map<String, String> expected) {
    final Map<String, String> check = new HashMap<>();
    if (expected != null) {
      check.putAll(expected);
    }
    for (Map.Entry<String, SVNPropertyValue> entry : props.asMap().entrySet()) {
      if (entry.getKey().startsWith(SVNProperty.SVN_ENTRY_PREFIX)) continue;
      Assert.assertEquals(entry.getValue().getString(), check.remove(entry.getKey()));
    }
    Assert.assertTrue(check.isEmpty());
  }

  @NotNull
  private SVNCommitInfo createFile(@NotNull SVNRepository repo, @NotNull String filePath, @NotNull String content, @Nullable Map<String, String> props) throws SVNException, IOException {
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
    if (props != null) {
      for (Map.Entry<String, String> entry : props.entrySet()) {
        editor.changeFileProperty(filePath, entry.getKey(), SVNPropertyValue.create(entry.getValue()));
      }
    }
    sendDeltaAndClose(editor, filePath, null, content);
    for (int i = 0; i < depth; ++i) {
      editor.closeDir();
    }
    editor.closeDir();
    return editor.closeEdit();
  }

  private void sendDeltaAndClose(@NotNull ISVNEditor editor, @NotNull String filePath, @Nullable String oldData, @Nullable String newData) throws SVNException, IOException {
    try (
        ByteArrayInputStream oldStream = new ByteArrayInputStream(oldData == null ? emptyBytes : oldData.getBytes(StandardCharsets.UTF_8));
        ByteArrayInputStream newStream = new ByteArrayInputStream(newData == null ? emptyBytes : newData.getBytes(StandardCharsets.UTF_8))
    ) {
      editor.applyTextDelta(filePath, null);
      SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
      String checksum = deltaGenerator.sendDelta(filePath, oldStream, 0, newStream, editor, true);
      editor.closeFile(filePath, checksum);
    }
  }

  private void checkFileContent(@NotNull SVNRepository repo, @NotNull String filePath, @NotNull String content) throws IOException, SVNException {
    try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      repo.getFile(filePath, repo.getLatestRevision(), null, stream);
      Assert.assertEquals(new String(stream.toByteArray(), StandardCharsets.UTF_8), content);
    }
  }
}
