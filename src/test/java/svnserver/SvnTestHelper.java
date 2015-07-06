/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper to testing svn repository.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class SvnTestHelper {
  @NotNull
  private static final byte[] emptyBytes = {};

  private SvnTestHelper() {
  }

  public static void checkFileProp(@NotNull SVNRepository repo, @NotNull String filePath, @Nullable Map<String, String> expected) throws SVNException {
    SVNProperties props = new SVNProperties();
    repo.getFile(filePath, repo.getLatestRevision(), props, null);
    checkProp(props, expected);
  }

  public static void checkDirProp(@NotNull SVNRepository repo, @NotNull String filePath, @Nullable Map<String, String> expected) throws SVNException {
    SVNProperties props = new SVNProperties();
    repo.getDir(filePath, repo.getLatestRevision(), props, new ArrayList<>());
    checkProp(props, expected);
  }

  private static void checkProp(@NotNull SVNProperties props, @Nullable Map<String, String> expected) {
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
  public static SVNCommitInfo createFile(@NotNull SVNRepository repo, @NotNull String filePath, @NotNull String content, @Nullable Map<String, String> props) throws SVNException, IOException {
    return createFile(repo, filePath, content.getBytes(StandardCharsets.UTF_8), props);
  }

  @NotNull
  public static SVNCommitInfo createFile(@NotNull SVNRepository repo, @NotNull String filePath, @NotNull byte[] content, @Nullable Map<String, String> props) throws SVNException, IOException {
    final ISVNEditor editor = repo.getCommitEditor("Create file: " + filePath, null, false, null);
    editor.openRoot(-1);
    int index = 0;
    int depth = 1;
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
    return editor.closeEdit();
  }

  @NotNull
  public static SVNCommitInfo deleteFile(@NotNull SVNRepository repo, @NotNull String filePath) throws SVNException, IOException {
    long latestRevision = repo.getLatestRevision();
    final ISVNEditor editor = repo.getCommitEditor("Delete file: " + filePath, null, false, null);
    editor.openRoot(-1);
    int index = 0;
    int depth = 1;
    while (true) {
      index = filePath.indexOf('/', index + 1);
      if (index < 0) {
        break;
      }
      editor.openDir(filePath.substring(0, index), -1);
      depth++;
    }
    editor.deleteEntry(filePath, latestRevision);
    for (int i = 0; i < depth; ++i) {
      editor.closeDir();
    }
    return editor.closeEdit();
  }

  @NotNull
  public static SVNCommitInfo modifyFile(@NotNull SVNRepository repo, @NotNull String filePath, @NotNull String newData, long fileRev) throws SVNException, IOException {
    return modifyFile(repo, filePath, newData.getBytes(StandardCharsets.UTF_8), fileRev);
  }

  @NotNull
  public static SVNCommitInfo modifyFile(@NotNull SVNRepository repo, @NotNull String filePath, @NotNull byte[] newData, long fileRev) throws SVNException, IOException {
    final ByteArrayOutputStream oldData = new ByteArrayOutputStream();
    repo.getFile(filePath, fileRev, null, oldData);

    final ISVNEditor editor = repo.getCommitEditor("Modify file: " + filePath, null, false, null);
    editor.openRoot(-1);
    int index = 0;
    int depth = 1;
    while (true) {
      index = filePath.indexOf('/', index + 1);
      if (index < 0) {
        break;
      }
      editor.openDir(filePath.substring(0, index), -1);
      depth++;
    }
    editor.openFile(filePath, fileRev);
    sendDeltaAndClose(editor, filePath, oldData.toByteArray(), newData);
    for (int i = 0; i < depth; ++i) {
      editor.closeDir();
    }
    return editor.closeEdit();
  }

  public static void sendDeltaAndClose(@NotNull ISVNEditor editor, @NotNull String filePath, @Nullable String oldData, @Nullable String newData) throws SVNException, IOException {
    sendDeltaAndClose(editor, filePath, oldData == null ? null : oldData.getBytes(StandardCharsets.UTF_8), newData == null ? null : newData.getBytes(StandardCharsets.UTF_8));
  }

  public static void sendDeltaAndClose(@NotNull ISVNEditor editor, @NotNull String filePath, @Nullable byte[] oldData, @Nullable byte[] newData) throws SVNException, IOException {
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

  public static void checkFileContent(@NotNull SVNRepository repo, @NotNull String filePath, @NotNull String content) throws IOException, SVNException {
    try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      repo.getFile(filePath, repo.getLatestRevision(), null, stream);
      Assert.assertEquals(new String(stream.toByteArray(), StandardCharsets.UTF_8), content);
    }
  }

  public static void checkFileContent(@NotNull SVNRepository repo, @NotNull String filePath, @NotNull byte[] content) throws IOException, SVNException {
    try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      repo.getFile(filePath, repo.getLatestRevision(), null, stream);
      Assert.assertEquals(stream.toByteArray(), content);
    }
  }
}
