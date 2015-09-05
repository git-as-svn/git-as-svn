/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.config.serializer.ConfigType;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
import svnserver.repository.VcsAccess;
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
@ConfigType("listMapping")
public class RepositoryListMappingConfig implements RepositoryMappingConfig {
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  @NotNull
  private Map<String, Entry> repositories = new TreeMap<>();

  public static class Entry {
    @NotNull
    private AccessConfig access = new AclConfig();
    @NotNull
    private RepositoryConfig repository;
  }

  @NotNull
  @Override
  public VcsRepositoryMapping create(@NotNull SharedContext context) throws IOException, SVNException {
    final RepositoryListMapping.Builder builder = new RepositoryListMapping.Builder();
    for (Map.Entry<String, Entry> entry : repositories.entrySet()) {
      final LocalContext local = new LocalContext(context, entry.getKey());
      local.add(VcsAccess.class, entry.getValue().access.create(local));
      builder.add(entry.getKey(), entry.getValue().repository.create(local));
    }
    return builder.build();
  }
}
