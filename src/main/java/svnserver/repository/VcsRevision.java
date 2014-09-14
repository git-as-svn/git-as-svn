/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import svnserver.StringHelper;

import java.io.IOException;
import java.util.Map;

/**
 * Revision info.
 *
 * @author a.navrotskiy
 */
public interface VcsRevision {
  int getId();

  @NotNull
  Map<String, String> getProperties(boolean includeInternalProps);

  long getDate();

  @NotNull
  default String getDateString() {
    return StringHelper.formatDate(getDate());
  }

  @Nullable
  String getAuthor();

  @Nullable
  String getLog();

  @Nullable
  VcsFile getFile(@NotNull String fullPath) throws IOException, SVNException;

  @NotNull
  Map<String, ? extends VcsLogEntry> getChanges() throws IOException, SVNException;

  @Nullable
  VcsCopyFrom getCopyFrom(@NotNull String fullPath);
}
