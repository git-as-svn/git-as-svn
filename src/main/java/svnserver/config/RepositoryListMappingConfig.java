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
import svnserver.repository.mapping.RepositoryListMapping;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Repository list mapping.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class RepositoryListMappingConfig implements RepositoryMappingConfig {
  @NotNull
  private Map<String, RepositoryConfig> repositories = new TreeMap<>();

  @NotNull
  public Map<String, RepositoryConfig> getRepositories() {
    return repositories;
  }

  public void setRepositories(@NotNull Map<String, RepositoryConfig> repositories) {
    this.repositories = repositories;
  }

  @NotNull
  @Override
  public VcsRepositoryMapping create(@NotNull DB cacheDb) throws IOException, SVNException {
    final RepositoryListMapping.Builder builder = new RepositoryListMapping.Builder();
    for (Map.Entry<String, RepositoryConfig> entry : repositories.entrySet()) {
      builder.add(entry.getKey(), entry.getValue().create(cacheDb));
    }
    return builder.build();
  }
}
