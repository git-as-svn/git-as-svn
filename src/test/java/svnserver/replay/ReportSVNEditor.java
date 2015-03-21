/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.replay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.TreeSet;

/**
 * ISVNEditor for comparing differ subversion servers behaviour.
 *
 * @author a.navrotskiy
 */
public class ReportSVNEditor implements ISVNEditor {
  @NotNull
  private final Deque<String> paths = new ArrayDeque<>();
  @NotNull
  private final Set<String> report = new TreeSet<>();
  private long targetRevision = 0;

  @Override
  public void targetRevision(long revision) throws SVNException {
    this.targetRevision = revision;
  }

  @Override
  public void openRoot(long revision) throws SVNException {
    paths.push("/");
    add("", "open-root: " + rev(revision));
  }

  @Override
  public void addDir(@NotNull String path, @Nullable String copyFromPath, long copyFromRevision) throws SVNException {
    paths.push(path);
    if (copyFromPath != null) {
      add("add-dir: " + copyFromPath + ", " + rev(copyFromRevision));
    } else {
      add("add-dir");
    }
  }

  @Override
  public void openDir(String path, long revision) throws SVNException {
    paths.push(path);
    add("open-dir: " + rev(revision));
  }

  @Override
  public void deleteEntry(String path, long revision) throws SVNException {
    add(path, "delete-entry: " + rev(revision));
  }

  @Override
  public void absentFile(String path) throws SVNException {
    add(path, "absent-file");
  }

  @Override
  public void absentDir(String path) throws SVNException {
    add(path, "absent-dir");
  }

  @Override
  public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    if (copyFromPath != null) {
      add(path, "add-file: " + copyFromPath + ", " + rev(copyFromRevision));
    } else {
      add(path, "add-file");
    }
  }

  @Override
  public void openFile(String path, long revision) throws SVNException {
    add(path, "open-file: " + rev(revision));
  }

  @Override
  public void closeDir() throws SVNException {
    paths.pop();
  }

  @Override
  public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
    add("change-dir-prop: " + name + (value == null ? " (removed)" : ""));
  }

  @Override
  public void changeFileProperty(String path, String name, SVNPropertyValue value) throws SVNException {
    add(path, "change-file-prop: " + name + (value == null ? " (removed)" : ""));
  }

  @Override
  public void applyTextDelta(String path, String baseChecksum) throws SVNException {
    add(path, "apply-text-delta: " + baseChecksum);
  }

  @Override
  public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
    add(path, "delta-chunk");
    return null;
  }

  @Override
  public void textDeltaEnd(String path) throws SVNException {
    add(path, "delta-end");
  }

  @Override
  public void closeFile(String path, String textChecksum) throws SVNException {
    add(path, "close-file: " + textChecksum);
  }

  @Override
  public void abortEdit() throws SVNException {
    add("/", "abort-edit");
  }

  @Override
  public SVNCommitInfo closeEdit() throws SVNException {
    return null;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (String line : report) {
      sb.append(line).append("\n");
    }
    return sb.toString();
  }

  @NotNull
  private String rev(long revision) {
    if (revision < 0) {
      return "rN";
    }
    return "r" + (targetRevision - revision);
  }

  private void add(@NotNull String line) {
    add(paths.getLast(), line);
  }

  private void add(@NotNull String path, @NotNull String line) {
    report.add(path + " - " + line);
  }
}
