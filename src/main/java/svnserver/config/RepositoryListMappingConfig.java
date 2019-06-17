/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import svnserver.StringHelper;
import svnserver.auth.ACL;
import svnserver.config.serializer.ConfigType;
import svnserver.context.LocalContext;
import svnserver.context.SharedContext;
import svnserver.repository.RepositoryMapping;
import svnserver.repository.VcsAccess;
import svnserver.repository.git.GitBranch;
import svnserver.repository.git.GitRepository;

import java.io.File;
import java.io.IOException;
import java.util.*;
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
  private Map<String, String[]> groups = new HashMap<>();

  @NotNull
  @Override
  public RepositoryMapping create(@NotNull SharedContext context, boolean canUseParallelIndexing) throws IOException {
    final NavigableMap<String, GitRepository> repos = new TreeMap<>();

    final Set<String> uniquePaths = new HashSet<>();
    repositories.values().stream().map(entry -> entry.repository.getPath()).forEach(s -> {
      if (!uniquePaths.add(new File(s).getAbsolutePath()))
        throw new IllegalStateException("Duplicate repositories in config: " + s);
    });

    for (Map.Entry<String, Entry> entry : repositories.entrySet()) {
      final LocalContext local = new LocalContext(context, entry.getKey());
      local.add(VcsAccess.class, new ACL(groups, entry.getValue().access));
      repos.put(StringHelper.normalizeDir(entry.getKey()), entry.getValue().repository.create(local));
    }

    final Consumer<GitBranch> init = repository -> {
      try {
        repository.updateRevisions();
      } catch (IOException | SVNException e) {
        throw new RuntimeException(String.format("[%s]: failed to initialize", repository), e);
      }
    };

    if (canUseParallelIndexing) {
      repos
          .values()
          .parallelStream()
          .flatMap(repo -> repo.getBranches().values().parallelStream())
          .forEach(init);
    } else {
      repos
          .values()
          .stream()
          .flatMap(repo -> repo.getBranches().values().stream())
          .forEach(init);
    }

    return () -> repos;
  }

  public static class Entry {
    /**
     * This should be Map<String, Map<String, AccessMode>> but snakeyaml doesn't support that. See https://bitbucket.org/asomov/snakeyaml/issues/361/list-does-not-create-property-objects
     */
    @NotNull
    private Map<String, Map<String, String>> access = new HashMap<>();
    @NotNull
    private GitRepositoryConfig repository;
  }
}
