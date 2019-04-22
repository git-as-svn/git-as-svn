/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.replay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

import java.io.OutputStream;

/**
 * Simple transparent wrapper for ISVNEditor.
 *
 * @author a.navrotskiy
 */
public abstract class SVNEditorWrapper implements ISVNEditor {
  @Nullable
  private final ISVNEditor editor;
  // Allow removing directory entries only before creation/updating (issue #123).
  private boolean allowDelete = false;
  private final boolean checkDelete;

  public SVNEditorWrapper(@Nullable ISVNEditor editor, boolean checkDelete) {
    this.editor = editor;
    this.checkDelete = checkDelete;
  }

  @Override
  public void targetRevision(long revision) throws SVNException {
    if (editor != null)
      editor.targetRevision(revision);
  }

  @Override
  public void openRoot(long revision) throws SVNException {
    if (editor != null)
      editor.openRoot(revision);
    allowDelete = true;
  }

  @Override
  public void deleteEntry(String path, long revision) throws SVNException {
    if (editor != null)
      editor.deleteEntry(path, revision);
    if (checkDelete) {
      Assert.assertTrue(allowDelete, "Removing from " + path + "#" + revision + " is not allowed");
    }
  }

  @Override
  public void absentDir(String path) throws SVNException {
    if (editor != null) editor.absentDir(path);
  }

  @Override
  public void absentFile(String path) throws SVNException {
    if (editor != null)
      editor.absentFile(path);
  }

  @Override
  public void addDir(@NotNull String path, @Nullable String copyFromPath, long copyFromRevision) throws SVNException {
    if (editor != null)
      editor.addDir(path, copyFromPath, copyFromRevision);
    allowDelete = true;
  }

  @Override
  public void openDir(String path, long revision) throws SVNException {
    if (editor != null)
      editor.openDir(path, revision);
    allowDelete = true;
  }

  @Override
  public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
    if (editor != null) editor.changeDirProperty(name, value);
  }

  @Override
  public void closeDir() throws SVNException {
    if (editor != null) editor.closeDir();
    allowDelete = false;
  }

  @Override
  public void addFile(@NotNull String path, @Nullable String copyFromPath, long copyFromRevision) throws SVNException {
    if (editor != null) editor.addFile(path, copyFromPath, copyFromRevision);
    allowDelete = false;
  }

  @Override
  public void openFile(String path, long revision) throws SVNException {
    if (editor != null)
      editor.openFile(path, revision);
    allowDelete = false;
  }

  @Override
  public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
    if (editor != null) editor.changeFileProperty(path, propertyName, propertyValue);
  }

  @Override
  public void closeFile(String path, String textChecksum) throws SVNException {
    if (editor != null) editor.closeFile(path, textChecksum);
  }

  @Override
  public SVNCommitInfo closeEdit() throws SVNException {
    return (editor != null) ? editor.closeEdit() : null;
  }

  @Override
  public void abortEdit() throws SVNException {
    if (editor != null)
      editor.abortEdit();
  }

  @Override
  public void applyTextDelta(String path, String baseChecksum) throws SVNException {
    if (editor != null) editor.applyTextDelta(path, baseChecksum);
  }

  @Override
  public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
    return (editor != null) ? editor.textDeltaChunk(path, diffWindow) : null;
  }

  @Override
  public void textDeltaEnd(String path) throws SVNException {
    if (editor != null) editor.textDeltaEnd(path);
  }
}
