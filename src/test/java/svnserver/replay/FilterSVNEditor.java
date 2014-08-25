package svnserver.replay;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

import java.io.OutputStream;

/**
 * Filter ISVNEditor events to remove svn:entry properties.
 *
 * @author a.navrotskiy
 */
public class FilterSVNEditor implements ISVNEditor {
  @NotNull
  private final ISVNEditor editor;

  public FilterSVNEditor(@NotNull ISVNEditor editor) {
    this.editor = editor;
  }

  @Override
  public void targetRevision(long revision) throws SVNException {
    editor.targetRevision(revision);
  }

  @Override
  public void openRoot(long revision) throws SVNException {
    editor.openRoot(revision);
  }

  @Override
  public void deleteEntry(String path, long revision) throws SVNException {
    editor.deleteEntry(path, revision);
  }

  @Override
  public void absentDir(String path) throws SVNException {
    editor.absentDir(path);
  }

  @Override
  public void absentFile(String path) throws SVNException {
    editor.absentFile(path);
  }

  @Override
  public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    editor.addDir(path, copyFromPath, copyFromRevision);
  }

  @Override
  public void openDir(String path, long revision) throws SVNException {
    editor.openDir(path, revision);
  }

  @Override
  public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
    if (!name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
      editor.changeDirProperty(name, value);
    }
  }

  @Override
  public void closeDir() throws SVNException {
    editor.closeDir();
  }

  @Override
  public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    editor.addFile(path, copyFromPath, copyFromRevision);
  }

  @Override
  public void openFile(String path, long revision) throws SVNException {
    editor.openFile(path, revision);
  }

  @Override
  public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
    if (!propertyName.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
      editor.changeFileProperty(path, propertyName, propertyValue);
    }
  }

  @Override
  public void closeFile(String path, String textChecksum) throws SVNException {
    editor.closeFile(path, textChecksum);
  }

  @Override
  public SVNCommitInfo closeEdit() throws SVNException {
    return editor.closeEdit();
  }

  @Override
  public void abortEdit() throws SVNException {
    editor.abortEdit();
  }

  @Override
  public void applyTextDelta(String path, String baseChecksum) throws SVNException {
    editor.applyTextDelta(path, baseChecksum);
  }

  @Override
  public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
    return editor.textDeltaChunk(path, diffWindow);
  }

  @Override
  public void textDeltaEnd(String path) throws SVNException {
    editor.textDeltaEnd(path);
  }
}
