/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import svnserver.repository.VcsCopyFrom;
import svnserver.repository.VcsLogEntry;

import java.io.IOException;
import java.util.Map;

/**
 * Git modification type.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class GitLogEntry implements VcsLogEntry {
  @NotNull
  private final GitLogPair pair;
  @Nullable
  private final VcsCopyFrom copyFrom;

  public GitLogEntry(@NotNull GitLogPair pair, @NotNull Map<String, VcsCopyFrom> renames) {
    this.pair = pair;
    this.copyFrom = pair.getNewEntry() != null ? renames.get(pair.getNewEntry().getFullPath()) : null;
  }

  @Override
  public char getChange() throws IOException, SVNException {
    if (pair.getNewEntry() == null)
      return SVNLogEntryPath.TYPE_DELETED;

    if (pair.getOldEntry() == null)
      return SVNLogEntryPath.TYPE_ADDED;

    if (pair.getNewEntry().getKind() != pair.getOldEntry().getKind())
      return SVNLogEntryPath.TYPE_REPLACED;

    return isModified() ? SVNLogEntryPath.TYPE_MODIFIED : 0;
  }

  @NotNull
  @Override
  public SVNNodeKind getKind() {
    if (pair.getNewEntry() != null)
      return pair.getNewEntry().getKind();

    if (pair.getOldEntry() != null)
      return pair.getOldEntry().getKind();

    throw new IllegalStateException();
  }

  @Nullable
  @Override
  public VcsCopyFrom getCopyFrom() {
    return copyFrom;
  }

  @Override
  public boolean isContentModified() throws IOException, SVNException {
    return pair.isContentModified();
  }

  @Override
  public boolean isPropertyModified() throws IOException {
    return pair.isPropertyModified();
  }

  @Override
  public boolean isModified() throws IOException, SVNException {
    return pair.isModified();
  }
}
