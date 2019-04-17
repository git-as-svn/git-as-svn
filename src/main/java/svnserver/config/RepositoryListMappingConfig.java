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
import svnserver.repository.git.GitRepository;
import svnserver.repository.mapping.RepositoryListMapping;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Repository list mapping.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
@ConfigType("listMapping")
public final class RepositoryListMappingConfig implements RepositoryMappingConfig {
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  @NotNull
  private Map<String, Entry> repositories = new TreeMap<>();

  @NotNull
  @Override
  public VcsRepositoryMapping create(@NotNull SharedContext context, boolean canUseParallelIndexing) throws IOException, SVNException {
    final Map<String, GitRepository> repos = new HashMap<>();

    for (Map.Entry<String, Entry> entry : repositories.entrySet()) {
      final LocalContext local = new LocalContext(context, entry.getKey());
      local.add(VcsAccess.class, entry.getValue().access.create(local));
      repos.put(entry.getKey(), entry.getValue().repository.create(local));
    }

    final Consumer<GitRepository> init = repository -> {
      try {
        repository.updateRevisions();
      } catch (IOException | SVNException e) {
        throw new RuntimeException(String.format("[%s]: failed to initialize", repository.getContext().getName()), e);
      }
    };

    if (canUseParallelIndexing) {
      repos.values().parallelStream().forEach(init);
    } else {
      repos.values().forEach(init);
    }

    return new RepositoryListMapping(repos);
  }

  public static class Entry {
    @NotNull
    private AccessConfig access = new AclConfig();
    @NotNull
    private RepositoryConfig repository;
  }
}
