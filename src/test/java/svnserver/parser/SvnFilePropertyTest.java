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
  public void commitUpdateProperties() throws Exception {
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
      {
        SVNProperties props = new SVNProperties();
        repo.getFile("/sample.txt", repo.getLatestRevision(), props, null);
        Assert.assertEquals(props.getStringValue(SVNProperty.EOL_STYLE), SVNProperty.EOL_STYLE_NATIVE);
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
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertTrue(e.getMessage().contains(SVNProperty.EOL_STYLE));
      }
    }
  }

  /**
   * Check commit .gitattributes with some files.
   *
   * @throws Exception
   */
  @Test(timeOut = 60 * 1000)
  public void commitWithFileProperties() throws Exception {
    try (SvnTestServer server = SvnTestServer.createEmpty()) {
      final SVNRepository repo = SVNRepositoryFactory.create(server.getUrl());
      repo.setAuthenticationManager(server.getAuthenticator());

      createFile(repo, "Create new file", "sample.txt", "", null);
      {
        SVNProperties props = new SVNProperties();
        repo.getFile("/sample.txt", repo.getLatestRevision(), props, null);
        Assert.assertEquals(props.getStringValue(SVNProperty.EOL_STYLE), null);
      }
      try {
        final ISVNEditor editor = repo.getCommitEditor("Create .gitattributes", null, false, null);
        editor.openRoot(-1);
        createFile(editor, ".gitattributes", "*.txt\t\t\ttext eol=native\n", null);
        createFile(editor, "none-props.txt", "", null);
        editor.closeDir();
        editor.closeEdit();
        Assert.fail();
      } catch (SVNException e) {
        Assert.assertTrue(e.getMessage().contains(SVNProperty.EOL_STYLE));
      }
      {
        final ISVNEditor editor = repo.getCommitEditor("Create .gitattributes", null, false, null);
        editor.openRoot(-1);
        createFile(editor, ".gitattributes", "*.txt\t\t\ttext eol=native\n", null);
        createFile(editor, "with-props.txt", "", SVNProperty.EOL_STYLE_NATIVE);
        editor.closeDir();
        editor.closeEdit();
      }
    }
  }

  @NotNull
  private SVNCommitInfo createFile(@NotNull SVNRepository repo, @NotNull String logMessage, @NotNull String filePath, @NotNull String content, @Nullable String eolStyle) throws SVNException {
    final ISVNEditor editor = repo.getCommitEditor(logMessage, null, false, null);
    editor.openRoot(-1);
    createFile(editor, filePath, content, eolStyle);
    editor.closeDir();
    return editor.closeEdit();
  }

  private void createFile(@NotNull ISVNEditor editor, @NotNull String filePath, @NotNull String content, @Nullable String eolStyle) throws SVNException {
    editor.addFile(filePath, null, -1);
    if (eolStyle != null) {
      editor.changeFileProperty(filePath, SVNProperty.EOL_STYLE, SVNPropertyValue.create(eolStyle));
    }
    editor.applyTextDelta(filePath, null);
    SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
    String checksum = deltaGenerator.sendDelta(filePath, new ByteArrayInputStream(new byte[0]), 0, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), editor, true);
    editor.closeFile(filePath, checksum);
  }
}
