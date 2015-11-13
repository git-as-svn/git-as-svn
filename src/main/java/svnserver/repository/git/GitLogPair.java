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
import svnserver.repository.SvnForbiddenException;
import svnserver.repository.git.filter.GitFilter;

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

    if (Objects.equals(filterName(newEntry), filterName(oldEntry))) {
      return !Objects.equals(newEntry.getObjectId(), oldEntry.getObjectId());
    } else {
      return !newEntry.getMd5().equals(oldEntry.getMd5());
    }
  }

  @Nullable
  private static String filterName(@NotNull GitFile gitFile) {
    final GitFilter filter = gitFile.getFilter();
    return filter == null ? null : filter.getName();
  }

  public boolean isPropertyModified() throws IOException, SVNException {
    if ((newEntry == null) || (oldEntry == null)) return false;
    final Map<String, String> newProps = newEntry.getProperties();
    final Map<String, String> oldProps = oldEntry.getProperties();
    return !Objects.equals(newProps, oldProps);
  }

  public boolean isModified() throws IOException, SVNException {
    try {
      if ((newEntry != null) && (oldEntry != null) && !newEntry.equals(oldEntry)) {
        // Type modified.
        if (!Objects.equals(newEntry.getFileMode(), oldEntry.getFileMode())) return true;
        // Content modified.
        if ((!newEntry.isDirectory()) && (!oldEntry.isDirectory())) {
          if (!Objects.equals(newEntry.getObjectId(), oldEntry.getObjectId())) return true;
        }
        // Probably properties modified
        final boolean sameProperties = Objects.equals(newEntry.getUpstreamProperties(), oldEntry.getUpstreamProperties())
            && Objects.equals(getFilterName(newEntry), getFilterName(oldEntry));
        if (!sameProperties) {
          return isPropertyModified();
        }
      }
      return false;
    } catch (SvnForbiddenException e) {
      // By default - entry is modified.
      return true;
    }
  }

  @Nullable
  private static String getFilterName(@NotNull GitFile file) {
    final GitFilter filter = file.getFilter();
    return filter == null ? null : filter.getName();
  }
}
