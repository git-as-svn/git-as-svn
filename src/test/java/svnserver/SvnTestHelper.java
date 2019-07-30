/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver;

import org.ini4j.Reg;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testcontainers.DockerClientFactory;
import org.testng.Assert;
import org.testng.SkipException;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
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

  public static void createFile(@NotNull SVNRepository repo, @NotNull String filePath, @NotNull String content, @Nullable Map<String, String> props) throws SVNException, IOException {
    createFile(repo, filePath, content.getBytes(StandardCharsets.UTF_8), props);
  }

  public static void createFile(@NotNull SVNRepository repo, @NotNull String filePath, @NotNull byte[] content, @Nullable Map<String, String> props) throws SVNException, IOException {
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
    Assert.assertNotEquals(editor.closeEdit(), SVNCommitInfo.NULL);
  }

  public static void deleteFile(@NotNull SVNRepository repo, @NotNull String filePath) throws SVNException {
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
    Assert.assertNotEquals(editor.closeEdit(), SVNCommitInfo.NULL);
  }

  public static void modifyFile(@NotNull SVNRepository repo, @NotNull String filePath, @NotNull String newData, long fileRev) throws SVNException, IOException {
    modifyFile(repo, filePath, newData.getBytes(StandardCharsets.UTF_8), fileRev);
  }

  public static void modifyFile(@NotNull SVNRepository repo, @NotNull String filePath, @NotNull byte[] newData, long fileRev) throws SVNException, IOException {
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
    Assert.assertNotEquals(editor.closeEdit(), SVNCommitInfo.NULL);
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
    checkFileContent(repo, filePath, content.getBytes(StandardCharsets.UTF_8));
  }

  public static void checkFileContent(@NotNull SVNRepository repo, @NotNull String filePath, @NotNull byte[] content) throws IOException, SVNException {
    final SVNDirEntry info = repo.info(filePath, repo.getLatestRevision());
    Assert.assertEquals(info.getKind(), SVNNodeKind.FILE);
    Assert.assertEquals(info.getSize(), content.length);

    try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      repo.getFile(filePath, repo.getLatestRevision(), null, stream);
      Assert.assertEquals(stream.toByteArray(), content);
    }
  }

  @Nullable
  public static String findExecutable(@NotNull String name) {
    final String path = System.getenv("PATH");
    if (path != null) {
      final String suffix = Reg.isWindows() ? ".exe" : "";
      for (String dir : path.split(File.pathSeparator)) {
        final File file = new File(dir, name + suffix);
        if (file.exists()) {
          return file.getAbsolutePath();
        }
      }
    }
    return null;
  }

  public static void skipTestIfDockerUnavailable() {
    try {
      Assert.assertNotNull(DockerClientFactory.instance().client());
    } catch (IllegalStateException e) {
      throw new SkipException("Docker is not available", e);
    }
  }
}
