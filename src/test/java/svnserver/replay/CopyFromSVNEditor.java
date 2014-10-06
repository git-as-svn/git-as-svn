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
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNEditor;

import java.util.Map;
import java.util.TreeMap;

/**
 * Collect copy-from information.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class CopyFromSVNEditor extends SVNEditorWrapper {
  @NotNull
  private final Map<String, String> copyFrom = new TreeMap<>();
  @NotNull
  private final String basePath;

  public CopyFromSVNEditor(@Nullable ISVNEditor editor, @NotNull String basePath) {
    super(editor);
    this.basePath = basePath;
  }

  @Override
  public void addDir(@NotNull String path, @Nullable String copyFromPath, long copyFromRevision) throws SVNException {
    if (copyFromPath != null) copyFrom.put(basePath + path, copyFromPath + "@" + copyFromRevision);
    super.addDir(path, copyFromPath, copyFromRevision);
  }

  @Override
  public void addFile(@NotNull String path, @Nullable String copyFromPath, long copyFromRevision) throws SVNException {
    if (copyFromPath != null) copyFrom.put(basePath + path, copyFromPath + "@" + copyFromRevision);
    super.addFile(path, copyFromPath, copyFromRevision);
  }

  @NotNull
  public Map<String, String> getCopyFrom() {
    return copyFrom;
  }
}
