/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * Git modification type.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class GitLogPair {
  @Nullable
  private final GitFile oldEntry;
  @Nullable
  private final GitFile newEntry;

  public GitLogPair(@Nullable GitFile oldEntry, @Nullable GitFile newEntry) {
    this.oldEntry = oldEntry;
    this.newEntry = newEntry;
  }

  @Nullable
  public GitFile getOldEntry() {
    return oldEntry;
  }

  @Nullable
  public GitFile getNewEntry() {
    return newEntry;
  }

  public boolean isContentModified() throws IOException, SVNException {
    if (newEntry == null || newEntry.isDirectory())
      return false;

    if (oldEntry == null || oldEntry.isDirectory())
      return false;

    if (newEntry.isSymlink() == oldEntry.isSymlink()) {
      return !Objects.equals(newEntry.getObjectId(), oldEntry.getObjectId());
    } else {
      return !newEntry.getMd5().equals(oldEntry.getMd5());
    }
  }

  public boolean isPropertyModified() throws IOException {
    if ((newEntry == null) || (oldEntry == null)) return false;
    final Map<String, String> newProps = newEntry.getProperties();
    final Map<String, String> oldProps = oldEntry.getProperties();
    return !Objects.equals(newProps, oldProps);
  }

  public boolean isModified() throws IOException, SVNException {
    if ((newEntry != null) && (oldEntry != null) && !newEntry.equals(oldEntry)) {
      // Type modified.
      if (!Objects.equals(newEntry.getFileMode(), oldEntry.getFileMode())) return true;
      // Content modified.
      if ((!newEntry.isDirectory()) && (!oldEntry.isDirectory())) {
        if (!Objects.equals(newEntry.getObjectId(), oldEntry.getObjectId())) return true;
      }
      // Probably properties modified
      if (!Objects.equals(newEntry.getUpstreamProperties(), oldEntry.getUpstreamProperties())) {
        return isPropertyModified();
      }
    }
    return false;
  }
}
