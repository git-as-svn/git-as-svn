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
import org.tmatesoft.svn.core.SVNURL;

import java.io.IOException;

/**
 * Resolving repository by URL.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface VcsRepositoryMapping {
  @Nullable
  RepositoryInfo getRepository(@NotNull SVNURL url) throws SVNException;

  /**
   * Update revision information in all mapped repositories.
   *
   * @throws IOException
   */
  void initRevisions() throws IOException, SVNException;
}
