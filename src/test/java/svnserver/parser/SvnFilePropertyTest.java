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
  /**
   * Check commit .gitattributes.
   *
   * @throws Exception
   */
  @Test(timeOut = 60 * 1000)
  public void commitUpdatePropertiesRoot() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());

      createFile(repo, "Create new file", "/sample.txt", "", null);
      {
        SVNProperties props = new SVNProperties();
        repo.getFile("/sample.txt", repo.getLatestRevision(), props, null);
        Assert.assertEquals(props.getStringValue(SVNProperty.EOL_STYLE), null);
      }
      createFile(repo, "Create .gitattributes", "/.gitattributes", "*.txt\t\t\ttext eol=native\n", null);
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

      createDir(repo, "/foo");
      createFile(repo, "Create new file", "/sample.txt", "", null);
      createFile(repo, "Create new file", "/foo/sample.txt", "", null);
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
      createFile(repo, "Create .gitattributes", "/foo/.gitattributes", "*.txt\t\t\ttext eol=native\n", null);
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
  public void commitWithProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());

      createFile(repo, "Create new file", "sample.txt", "", null);
      {
        SVNProperties props = new SVNProperties();
        repo.getFile("/sample.txt", repo.getLatestRevision(), props, null);
        Assert.assertEquals(props.getStringValue(SVNProperty.EOL_STYLE), null);
      }
      createFile(repo, "Create .gitattributes", ".gitattributes", "*.txt\t\t\ttext eol=native\n", null);
      createFile(repo, "Create new file", "with-props.txt", "", SVNProperty.EOL_STYLE_NATIVE);
      try {
        createFile(repo, "Create new file", "none-props.txt", "", null);
      } catch (SVNException e) {
        Assert.assertTrue(e.getMessage().contains(SVNProperty.EOL_STYLE));
      }
    }
  }

  @NotNull
  private SVNCommitInfo createDir(@NotNull SVNRepository repo, @NotNull String filePath) throws SVNException {
    final ISVNEditor editor = repo.getCommitEditor("Create directory: " + filePath, null, false, null);
    editor.openRoot(-1);
    int index = 0;
    int depth = 0;
    while (true) {
      index = filePath.indexOf('/', index + 1);
      if (index < 0) {
        editor.addDir(filePath, null, -1);
        depth++;
        break;
      }
      editor.openDir(filePath.substring(0, index), -1);
      depth++;
    }
    // Empty file.
    final String emptyFile = filePath + "/.keep";
    editor.addFile(emptyFile, null, -1);
    editor.applyTextDelta(emptyFile, null);
    String checksum = new SVNDeltaGenerator().sendDelta(emptyFile, new ByteArrayInputStream(new byte[0]), 0, new ByteArrayInputStream(new byte[0]), editor, true);
    editor.closeFile(emptyFile, checksum);

    for (int i = 0; i < depth; ++i) {
      editor.closeDir();
    }
    editor.closeDir();
    return editor.closeEdit();
  }

  @NotNull
  private SVNCommitInfo createFile(@NotNull SVNRepository repo, @NotNull String logMessage, @NotNull String filePath, @NotNull String content, @Nullable String eolStyle) throws SVNException {
    final ISVNEditor editor = repo.getCommitEditor(logMessage, null, false, null);
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
    editor.applyTextDelta(filePath, null);
    SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
    String checksum = deltaGenerator.sendDelta(filePath, new ByteArrayInputStream(new byte[0]), 0, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), editor, true);
    editor.closeFile(filePath, checksum);
    for (int i = 0; i < depth; ++i) {
      editor.closeDir();
    }
    editor.closeDir();
    return editor.closeEdit();
  }
}
