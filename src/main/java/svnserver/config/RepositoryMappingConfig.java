/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.tmatesoft.svn.core.SVNException;
import svnserver.repository.VcsRepositoryMapping;

import java.io.File;
import java.io.IOException;

/**
 * Repository mapping config.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public interface RepositoryMappingConfig {
  @NotNull
  VcsRepositoryMapping create(@NotNull File basePath, @NotNull DB cacheDb) throws IOException, SVNException;
}
