/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.replay;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

import java.io.OutputStream;
import java.util.*;

/**
 * ISVNEditor for comparing revisions from two repositories.
 *
 * @author a.navrotskiy
 */
public class ExportSVNEditor implements ISVNEditor {
  @NotNull
  private final Deque<String> paths = new ArrayDeque<>();
  @NotNull
  private final Map<String, String> files = new TreeMap<>();
  @NotNull
  private final Map<String, Map<String, String>> properties = new HashMap<>();

  @Override
  public void targetRevision(long revision) throws SVNException {
  }

  @Override
  public void openRoot(long revision) throws SVNException {
    paths.push("/");
    files.put("/", "dir");
  }

  @Override
  public void deleteEntry(String path, long revision) throws SVNException {
  }

  @Override
  public void absentDir(String path) throws SVNException {
  }

  @Override
  public void absentFile(String path) throws SVNException {
  }

  @Override
  public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    paths.push(path);
    files.put(path, "directory");
  }

  @Override
  public void openDir(String path, long revision) throws SVNException {
    paths.push(path);
    files.put(path, "directory");
  }

  @Override
  public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
    properties.computeIfAbsent(paths.element(), (s) -> new TreeMap<>()).put(name, value.getString());
  }

  @Override
  public void closeDir() throws SVNException {
    paths.pop();
  }

  @Override
  public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
  }

  @Override
  public void openFile(String path, long revision) throws SVNException {
  }

  @Override
  public void changeFileProperty(String path, String name, SVNPropertyValue value) throws SVNException {
    properties.computeIfAbsent(paths.element(), (s) -> new TreeMap<>()).put(name, value.getString());
  }

  @Override
  public void closeFile(String path, String textChecksum) throws SVNException {
    files.put(path, textChecksum);
  }

  @Override
  public SVNCommitInfo closeEdit() throws SVNException {
    return null;
  }

  @Override
  public void abortEdit() throws SVNException {
  }

  @Override
  public void applyTextDelta(String path, String baseChecksum) throws SVNException {
  }

  @Override
  public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
    return null;
  }

  @Override
  public void textDeltaEnd(String path) throws SVNException {
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Files:\n");
    for (Map.Entry<String, String> entry : files.entrySet()) {
      sb.append("  ").append(entry.getKey()).append(" (").append(entry.getValue()).append(")\n");
      final Map<String, String> props = properties.get(entry.getKey());
      if (props != null) {
        for (Map.Entry<String, String> prop : props.entrySet()) {
          sb.append("    ").append(prop.getKey()).append(" = \"").append(prop.getValue()).append("\"\n");
        }
      }
    }
    return sb.toString();
  }
}
